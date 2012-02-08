/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.sql.optimizer.rule.join_enum;

import com.akiban.sql.optimizer.plan.*;
import static com.akiban.sql.optimizer.plan.JoinNode.JoinType;

import java.util.*;

/** 
 * Hypergraph-based dynamic programming for join ordering enumeration.
 * See "Dynamic Programming Strikes Back", doi:10.1145/1376616.1376672
 *
 * Summary:<ul>
 * <li>Nodes are joined tables.</li>
 * <li>Edges represent predicates and operator (LEFT, FULL OUTER,
 * SEMI, ...) reordering constraints.</li>
 * <li>DP happens by considering larger
 * sets made up from pairs of connected (based on edges) subsets.</li></ul>
 */
public abstract class DPhyp<P>
{
    // The leaves of the join tree: tables, derived tables, and
    // possibly joins handled atomically wrt this phase.
    private List<Joinable> tables;
    // The join operators.
    private List<JoinNode> joins;
    // The hypergraph: since these are unordered, traversal pattern is
    // to go through in order pairing with adjacent (complement bit 1).
    private long[] edges;
    
    // The "plan class" is the set of retained plans for the given tables.
    private Object[] plans;
    
    private P getPlan(long s) {
        return (P)plans[(int)s];
    }
    private void setPlan(long s, P plan) {
        if (false)
            System.out.println(JoinableBitSet.toString(s, tables) + ": " + plan);
        plans[(int)s] = plan;
    }

    public P run(Joinable root) {
        init(root);
        return solve();
    }

    /** Return minimal set of neighbors of <code>s</code> not in the exclusion set. */
    // TODO: Need to eliminate subsumed hypernodes.
    public long neighborhood(long s, long exclude) {
        exclude = JoinableBitSet.union(s, exclude);
        long result = 0;
        for (int e = 0; e < edges.length; e++) {
            if (JoinableBitSet.isSubset(edges[e], s) &&
                !JoinableBitSet.overlaps(edges[e^1], exclude)) {
                result |= JoinableBitSet.minSubset(edges[e^1]);
            }
        }
        return result;
    }

    /** Run dynamic programming and return best overall plan(s). */
    public P solve() {
        int ntables = tables.size();
        plans = new Object[1 << ntables];
        // Start with single tables.
        for (int i = 0; i < ntables; i++) {
            setPlan(JoinableBitSet.of(i), evaluateTable(tables.get(i)));
        }
        for (int i = ntables - 1; i >= 0; i--) {
            long ts = JoinableBitSet.of(i);
            emitCsg(ts);
            enumerateCsgRec(ts, JoinableBitSet.through(i));
        }
        return getPlan(plans.length-1); // One that does all the joins together.
    }

    /** Recursively extend the given connected subgraph. */
    public void enumerateCsgRec(long s1, long exclude) {
        long neighborhood = neighborhood(s1, exclude);
        if (neighborhood == 0) return;
        long subset = 0;
        do {
            subset = JoinableBitSet.nextSubset(subset, neighborhood);
            long next = JoinableBitSet.union(s1, subset);
            if (getPlan(next) != null)
                emitCsg(next);
        } while (!JoinableBitSet.equals(subset, neighborhood));
        subset = 0;             // Start over.
        exclude = JoinableBitSet.union(exclude, neighborhood);
        do {
            subset = JoinableBitSet.nextSubset(subset, neighborhood);
            long next = JoinableBitSet.union(s1, subset);
            enumerateCsgRec(next, exclude);
        } while (!JoinableBitSet.equals(subset, neighborhood));
    }

    /** Generate seeds for connected complements of the given connected subgraph. */
    public void emitCsg(long s1) {
        long exclude = JoinableBitSet.union(s1, JoinableBitSet.through(JoinableBitSet.min(s1)));
        long neighborhood = neighborhood(s1, exclude);
        if (neighborhood == 0) return;
        for (int i = tables.size() - 1; i >= 0; i--) {
            long s2 = JoinableBitSet.of(i);
            if (JoinableBitSet.overlaps(neighborhood, s2)) {
                boolean connected = false;
                for (int e = 0; e < edges.length; e++) {
                    if (JoinableBitSet.isSubset(edges[e], s1) &&
                        JoinableBitSet.isSubset(edges[e^1], s2)) {
                        connected = true;
                        break;
                    }
                }
                if (connected)
                    emitCsgCmp(s1, s2);
                enumerateCmpRec(s1, s2, exclude);
            }
        }        
    }

    /** Extend complement <code>s2</code> until a csg-cmp pair is
     * reached, excluding the given tables to avoid duplicate
     * enumeration, */
    public void enumerateCmpRec(long s1, long s2, long exclude) {
        long neighborhood = neighborhood(s2, exclude);
        if (neighborhood == 0) return;
        long subset = 0;
        do {
            subset = JoinableBitSet.nextSubset(subset, neighborhood);
            long next = JoinableBitSet.union(s2, subset);
            if (getPlan(next) != null) {
                boolean connected = false;
                for (int e = 0; e < edges.length; e++) {
                    if (JoinableBitSet.isSubset(edges[e], s1) &&
                        JoinableBitSet.isSubset(edges[e^1], next)) {
                        connected = true;
                        break;
                    }
                }
                if (connected)
                    emitCsgCmp(s1, next);
            }
        } while (!JoinableBitSet.equals(subset, neighborhood));
        subset = 0;             // Start over.
        exclude = JoinableBitSet.union(exclude, neighborhood);
        do {
            subset = JoinableBitSet.nextSubset(subset, neighborhood);
            long next = JoinableBitSet.union(s2, subset);
            enumerateCmpRec(s1, next, exclude);
        } while (!JoinableBitSet.equals(subset, neighborhood));
    }

    /** Emit the connected subgraph / complement pair.
     * That is, build and cost a plan that joins them and maybe
     * register it as the new best such plan for the pair.
     */
    public void emitCsgCmp(long s1, long s2) {
        P p1 = getPlan(s1);
        P p2 = getPlan(s2);
        long s = JoinableBitSet.union(s1, s2);
        JoinNode operator = null;
        for (int e = 0; e < edges.length; e++) {
            if (JoinableBitSet.isSubset(edges[e], s1) &&
                JoinableBitSet.isSubset(edges[e^1], s2)) {
                // TODO: When can more than one apply?
                assert (operator == null);
                operator = joins.get(e/2); // The one that produced this edge.
            }
        }
        assert (operator != null);
        P plan = getPlan(s);
        plan = evaluateJoin(p1, p2, plan, 
                            operator.getJoinType(), operator.getJoinConditions());
        if (isCommutative(operator.getJoinType()))
            plan = evaluateJoin(p2, p1, plan, 
                                operator.getJoinType(), operator.getJoinConditions());
        setPlan(s, plan);
    }

    public abstract P evaluateTable(Joinable table);
    public abstract P evaluateJoin(P p1, P p2, P existing, 
                                   JoinType joinType, ConditionList joinConditions);

    // TODO: Maybe move these into members of the plan nodes?
    private Map<Joinable,Long> ses;
    private Map<JoinNode,Long> pes, tes;

    private long getTables(Joinable n) {
        return ses.get(n);
    }
    private void setTables(Joinable n, long t) {
        ses.put(n, t);
    }
    private long getPredicateTables(JoinNode n) {
        return pes.get(n);
    }
    private void setPredicateTables(JoinNode n, long t) {
        pes.put(n, t);
    }
    private long getTES(JoinNode n) {
        return tes.get(n);
    }
    private void setTES(JoinNode n, long t) {
        tes.put(n, t);
    }

    /** Initialize state from the given join tree. */
    public void init(Joinable root) {
        tables = new ArrayList<Joinable>();
        addTables(root);
        ses = new HashMap<Joinable,Long>();
        pes = new HashMap<JoinNode,Long>();
        tes = new HashMap<JoinNode,Long>();
        initSES(root);
        int njoins = tes.keySet().size();
        joins = new ArrayList<JoinNode>(njoins);
        edges = new long[njoins * 2];
        calcTES(root);
        if (false) {
            for (int e = 0; e < njoins; e++) {
                System.out.println(JoinableBitSet.toString(edges[e*2], tables) + " <-> " +
                                   JoinableBitSet.toString(edges[e*2+1], tables));
            }
        }
        ses = null;
        pes = tes = null;
    }

    protected void addTables(Joinable n) {
        if (n instanceof JoinNode) {
            JoinNode join = (JoinNode)n;
            addTables(join.getLeft());
            addTables(join.getRight());
        }
        else {
            tables.add(n);
        }
    }

    /** Starting state of the TES is just those tables used syntactically. */
    protected long initSES(Joinable n) {
        long t;
        if (n instanceof JoinNode) {
            JoinNode join = (JoinNode)n;
            t = JoinableBitSet.union(initSES(join.getLeft()),
                                     initSES(join.getRight()));
            long p = predicateTables(join.getJoinConditions());
            pes.put(join, p);
            tes.put(join, JoinableBitSet.intersection(t, p));
        }
        else {
            t = JoinableBitSet.of(tables.indexOf(n));
        }
        ses.put(n, t);
        return t;
    }

    /** Extend TES for join operators based on reordering conflicts. */
    public void calcTES(Joinable n) {
        if (n instanceof JoinNode) {
            JoinNode join = (JoinNode)n;
            calcTES(join.getLeft());
            calcTES(join.getRight());
            addConflicts(join, join.getLeft(), true);
            addConflicts(join, join.getRight(), false);
            long t = getTES(join);
            long s = getTables(join.getRight());
            long r = JoinableBitSet.intersection(t, s);
            long l = JoinableBitSet.difference(t, r);
            int o = joins.size();
            joins.add(join);    // Remember operator for join type and predicate.
            // Add an edge for the TES of this join operator.
            edges[o*2] = l;
            edges[o*2+1] = r;
        }
    }

    /** Add conflicts to <code>o1</code> from descendant <code>j</code>. */
    protected void addConflicts(JoinNode o1, Joinable j, boolean left) {
        if (j instanceof JoinNode) {
            JoinNode o2 = (JoinNode)j;
            if (left ? leftConflict(o2, o1) : rightConflict(o1, o2)) {
                setTES(o1, JoinableBitSet.union(getTES(o1), getTES(o2)));
            }
            addConflicts(o1, o2.getLeft(), left);
            addConflicts(o1, o2.getRight(), left);
        }
    }

    /** Is there a left ordering conflict? */
    protected boolean leftConflict(JoinNode o2, JoinNode o1) {
        return JoinableBitSet.overlaps(getPredicateTables(o1), rightTables(o1, o2)) &&
            operatorConflict(o2.getJoinType(), o1.getJoinType());
    }

    /** Is there a right ordering conflict? */
    protected boolean rightConflict(JoinNode o1, JoinNode o2) {
        return JoinableBitSet.overlaps(getPredicateTables(o1), leftTables(o1, o2)) &&
            operatorConflict(o1.getJoinType(), o2.getJoinType());
    }

    /** Does parent opertor <code>o1</code> conflict with child <code>o2</code>? */
    protected boolean operatorConflict(JoinType o1, JoinType o2) {
        switch (o1) {
        case INNER:
            return (o2 == JoinType.FULL_OUTER);
        case LEFT:
            return (o2 != JoinType.LEFT);
        case FULL_OUTER:
            return (o2 == JoinType.INNER);
        default:
            return true;
        }
    }

    /** All the left tables on the path from <code>o2</code> (inclusive) 
     * to <code>o1</code> (exclusive). */
    protected long leftTables(JoinNode o1, JoinNode o2) {
        long result = JoinableBitSet.empty();
        for (JoinNode o3 = o2; o3 != o1; o3 = (JoinNode)o3.getOutput())
            result = JoinableBitSet.union(result, getTables(o3.getLeft()));
        if (isCommutative(o2.getJoinType()))
            result = JoinableBitSet.union(result, getTables(o2.getRight()));
        return result;
    }

    /** All the right tables on the path from <code>o2</code> (inclusive) 
     * to <code>o1</code> (exclusive). */
    protected long rightTables(JoinNode o1, JoinNode o2) {
        long result = JoinableBitSet.empty();
        for (JoinNode o3 = o2; o3 != o1; o3 = (JoinNode)o3.getOutput())
            result = JoinableBitSet.union(result, getTables(o3.getRight()));
        if (isCommutative(o2.getJoinType()))
            result = JoinableBitSet.union(result, getTables(o2.getLeft()));
        return result;
    }

    /** Does this operator commute? */
    protected boolean isCommutative(JoinType joinType) {
        switch (joinType) {
        case INNER:
        case FULL_OUTER:
            return true;
        default:
            return false;
        }
    }

    /** Compute tables used in join predicate. */
    // TODO: Also need to record whether null-tolerant and prevent more ordering if so.
    protected long predicateTables(ConditionList conditions) {
        ExpressionTables visitor = new ExpressionTables(tables);
        if (conditions != null) {
            for (ConditionExpression condition : conditions) {
                condition.accept(visitor);
            }
        }
        return visitor.result;
    }

    static class ExpressionTables implements ExpressionVisitor, PlanVisitor {
        List<Joinable> tables;
        long result = JoinableBitSet.empty();

        public ExpressionTables(List<Joinable> tables) {
            this.tables = tables;
        }

        @Override
        public boolean visitEnter(ExpressionNode n) {
            return visit(n);
        }

        @Override
        public boolean visitLeave(ExpressionNode n) {
            return true;
        }

        @Override
        public boolean visit(ExpressionNode n) {
            if (n instanceof ColumnExpression) {
                int idx = tables.indexOf(((ColumnExpression)n).getTable());
                if (idx >= 0) {
                    result = JoinableBitSet.union(result, JoinableBitSet.of(idx));
                }
            }
            return true;
        }

        @Override
        public boolean visitEnter(PlanNode n) {
            return visit(n);
        }

        @Override
        public boolean visitLeave(PlanNode n) {
            return true;
        }

        @Override
        public boolean visit(PlanNode n) {
            return true;
        }
    }
    
}
