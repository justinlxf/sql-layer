/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.server.test.it.keyupdate;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akiban.ais.model.Index;
import com.akiban.server.api.dml.scan.NewRow;

public class GroupIndexCascadeUpdateIT extends GIUpdateITBase {
    private static final Logger LOG = LoggerFactory.getLogger(GroupIndexCascadeUpdateIT.class);

    @Test
    public void deleteSecondOinCO() {
        groupIndex("c.name, o.when");
        final NewRow customer, firstOrder, secondOrder;
        writeAndCheck(
                customer = createNewRow(c, 1L, "Joe"),
                "Joe, null, 1, null => " + containing(c)
        );
        writeAndCheck(
                firstOrder = createNewRow(o, 11L, 1L, "01-01-01"),
                "Joe, 01-01-01, 1, 11 => " + containing(c, o)
        );
        writeAndCheck(
                secondOrder = createNewRow(o, 12L, 1L, "02-02-02"),
                "Joe, 01-01-01, 1, 11 => " + containing(c, o),
                "Joe, 02-02-02, 1, 12 => " + containing(c, o)
        );
        deleteCascadeAndCheck(
                secondOrder,
                "Joe, 01-01-01, 1, 11 => " + containing(c, o)
        );
        deleteCascadeAndCheck(
                firstOrder,
                "Joe, null, 1, null => " + containing(c)
        );
        deleteCascadeAndCheck(
                customer
        );
    }
     
    @Test
    public void deleteFromRoot() {
        String indexName = groupIndex("c.name, o.when, i.sku");
        writeRows(
                createNewRow(c, 2L, "David"),
                createNewRow(o, 12L, 2L, "01-01-2001"),
                createNewRow(i, 102L, 12L, 1111),
                createNewRow(h, 1002L, 102L, "handle with 2 care")
        );
        checkIndex(indexName, "David, 01-01-2001, 1111, 2, 12, 102 => " + containing(c, o, i));
        dml().deleteRow(session(), createNewRow(c, 2L, "David"), true);
        checkIndex(indexName);
    }
    
    @Test
    public void deleteBelowIndex() {
        String indexName = groupIndex("c.name, o.when");
        writeRows(
                createNewRow(c, 1L, "Horton"),
                createNewRow(o, 11L, 1L, "01-01-2001"),
                createNewRow(i, 101L, 11L, 1111),
                createNewRow(h, 1001L, 101L, "handle with care")
        );
        checkIndex(indexName, "Horton, 01-01-2001, 1, 11 => " + containing(c, o));
        dml().deleteRow(session(), createNewRow(i, 101L, 11L, 1111), true);
        checkIndex(indexName, "Horton, 01-01-2001, 1, 11 => " + containing(c, o));
        
    }
    
    @Test
    public void multipleIndexes() {
        createGroupIndex(groupName, "gi1", "c.name, o.when", Index.JoinType.LEFT);
        createGroupIndex(groupName, "gi2", "i.sku, h.handling_instructions", Index.JoinType.LEFT);
        writeRows(
                createNewRow(c, 1L, "Horton"),
                createNewRow(o, 11L, 1L, "01-01-2001"),
                createNewRow(i, 101L, 11L, 1111),
                createNewRow(h, 1001L, 101L, "handle with care")
        );
        checkIndex("gi1", "Horton, 01-01-2001, 1, 11 => " + containing("gi1", c, o));
        checkIndex("gi2", "1111, handle with care, 1, 11, 101, 1001 => " + containing("gi2", i, h));
        dml().deleteRow(session(), createNewRow(i, 101L, 11L, 1111), true);
        checkIndex("gi1", "Horton, 01-01-2001, 1, 11 => " + containing("gi1", c, o));
        checkIndex("gi2");
        
    }
    
    @Test
    public void branches () {
        createGroupIndex(groupName, "gi1", "c.name, o.when", Index.JoinType.LEFT);
        createGroupIndex(groupName, "gi2", "c.name, a.street", Index.JoinType.LEFT);
        writeRows(
                createNewRow(c, 4L, "Fred"),
                createNewRow(o, 14L, 4L, "01-01-2004"),
                createNewRow(i, 104L, 14L, 1111),
                createNewRow(h, 1004L, 104L, "handle with 4 care"),
                createNewRow(a, 21L, 4L, "street with no name")
        );
        checkIndex("gi1", "Fred, 01-01-2004, 4, 14 => " + containing("gi1", c, o));
        checkIndex("gi2", "Fred, street with no name, 4, 21 => " + containing("gi2", c, a));
        dml().deleteRow(session(), createNewRow(c, 4L, "Fred"), true);
        checkIndex("gi1");
        checkIndex("gi2");
    }
    
    @Test
    public void testPartial1Level() {
        String indexName = groupIndex("c.name, o.when, i.sku");
        writeRows(
                createNewRow(c, 5L, "James"),
                createNewRow(o, 15L, 5L, "01-01-2005"),
                createNewRow(i, 105L, 15L, 1111),
                createNewRow(h, 1005L, 105L, "handle with 5 care")
        );
        checkIndex(indexName, "James, 01-01-2005, 1111, 5, 15, 105 => " + containing(c, o, i));
        dml().deleteRow(session(), createNewRow(i, 105L, 15L, 1111), true);
        checkIndex(indexName, "James, 01-01-2005, null, 5, 15, null => " + containing(c, o));
        
    }

    @Test
    public void testPartial2Level() {
        String indexName = groupIndex("c.name, o.when, i.sku");
        writeRows(
                createNewRow(c, 6L, "Larry"),
                createNewRow(o, 16L, 6L, "01-01-2006"),
                createNewRow(i, 106L, 16L, 1111),
                createNewRow(h, 1006L, 106L, "handle with 6 care")
        );
        checkIndex(indexName, "Larry, 01-01-2006, 1111, 6, 16, 106 => " + containing(c, o, i));
        dml().deleteRow(session(), createNewRow(o, 16L, 6L, "01-01-2006"), true);
        checkIndex(indexName, "Larry, null, null, 6, null, null => " + containing(c));
        
    }

    
    public GroupIndexCascadeUpdateIT() {
        super(Index.JoinType.LEFT);
    }
}