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

package com.akiban.server.test.it.dxl;

import com.akiban.ais.model.TableName;
import com.akiban.ais.model.UserTable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

/**
 * Group altering actions on standard CAOI schema.
 */
public class AlterTableCAOIIT extends AlterTableITBase {
    private static final String A_TABLE = "a";
    private static final TableName A_NAME = new TableName(SCHEMA, A_TABLE);
    private int cid;
    private int aid;
    private int oid;
    private int iid;

    @After @Before
    public void clearTableIDs() {
        cid = aid = oid = iid = -1;
    }

    @Override
    protected void createAndLoadCAOI_PK_FK(boolean cPK, boolean aPK, boolean aFK, boolean oPK, boolean oFK, boolean iPK, boolean iFK) {
        cid = createTable(C_NAME, "id int not null "+(cPK ? "primary key" : "")+", cc varchar(5)" );
        aid = createTable(A_NAME, "id int not null "+(aPK ? "primary key" : "")+", cid int, aa varchar(5)"+
                (aFK ? "," + akibanFK("cid", C_TABLE, "id") : ""));
        oid = createTable(O_NAME, "id int not null "+(oPK ? "primary key" : "")+", cid int, oo varchar(5)"+
                (oFK ? "," + akibanFK("cid", C_TABLE, "id") : ""));
        iid = createTable(I_NAME, "id int not null "+(iPK ? "primary key" : "")+", oid int, ii varchar(5)"+
                (iFK ? "," + akibanFK("oid", O_TABLE, "id") : ""));
        // Index fk column
        createIndex(SCHEMA, A_TABLE, "cid", "cid");
        createIndex(SCHEMA, O_TABLE, "cid", "cid");
        createIndex(SCHEMA, I_TABLE, "oid", "oid");
        // Index on non-pk, non-fk column
        createIndex(SCHEMA, C_TABLE, "cc", "cc");
        createIndex(SCHEMA, A_TABLE, "aa", "aa");
        createIndex(SCHEMA, O_TABLE, "oo", "oo");
        createIndex(SCHEMA, I_TABLE, "ii", "ii");
        writeRows(
                createNewRow(cid, 1L, "1"),
                    createNewRow(aid, 10L, 1L, "11"),
                    createNewRow(oid, 10L, 1L, "11"),
                        createNewRow(iid, 100L, 10L, "110"),
                        createNewRow(iid, 101L, 10L, "111"),
                    createNewRow(oid, 11L, 1L, "12"),
                        createNewRow(iid, 111L, 11L, "122"),
                createNewRow(cid, 2L, "2"),                     // No children
                // No cust(3L)
                    createNewRow(oid, 30L, 3L, "33"),           // Level 1 orphan
                        createNewRow(iid, 300L, 30L, "330"),
                createNewRow(cid, 4L, "4"),
                    createNewRow(aid, 40L, 4L, "44"),
                    createNewRow(aid, 41L, 4L, "45"),
                    // No 40 order
                        createNewRow(iid, 400L, 40L, "440"),    // Level 2 orphan
                        createNewRow(iid, 401L, 40L, "441"),    // Level 2 orphan
                // No cust(5L)
                    createNewRow(aid, 50L, 5L, "55")            // Level 1 orphan
        );
    }

    private void groupsMatch(TableName name1, TableName... names) {
        UserTable t1 = getUserTable(name1);
        for(TableName name : names) {
            UserTable t2 = getUserTable(name);
            assertSame("Groups match for " + name1 + " and " + name, t1.getGroup(), t2.getGroup());
        }
    }

    private void groupsDiffer(TableName name1, TableName... names) {
        UserTable t1 = getUserTable(name1);
        for(TableName name : names) {
            UserTable t2 = getUserTable(name);
            assertNotSame("Groups differ for " + name1 + " and " + name, t1.getGroup(), t2.getGroup());
        }
    }


    //
    // SET DATA TYPE, parent column
    //

    @Test
    public void setDataType_C_id() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE " + C_TABLE + " ALTER COLUMN id SET DATA TYPE varchar(32)");
        groupsDiffer(C_NAME, A_NAME, O_NAME, I_NAME);
        groupsDiffer(A_NAME, O_NAME, I_NAME);
        groupsMatch(O_NAME, I_NAME);
    }

    @Test
    public void setDataType_A_id() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE " + A_TABLE + " ALTER COLUMN id SET DATA TYPE varchar(32)");
        groupsMatch(C_NAME, A_NAME, O_NAME, I_NAME);
    }

    @Test
    public void setDataType_O_id() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE " + O_TABLE + " ALTER COLUMN id SET DATA TYPE varchar(32)");
        groupsDiffer(I_NAME, C_NAME, A_NAME);
        groupsMatch(C_NAME, A_NAME, O_NAME);
    }

    @Test
    public void setDataType_I_id() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE " + I_TABLE + " ALTER COLUMN id SET DATA TYPE varchar(32)");
        groupsMatch(C_NAME, A_NAME, O_NAME, I_NAME);
    }

    //
    // SET DATA TYPE, child column
    //

    @Test
    public void setDataType_A_cid() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE "+A_TABLE+" ALTER COLUMN cid SET DATA TYPE varchar(32)");
        groupsDiffer(C_NAME, A_NAME);
        groupsMatch(C_NAME, O_NAME, I_NAME);
    }

    @Test
    public void setDataType_O_cid() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE " + O_TABLE + " ALTER COLUMN cid SET DATA TYPE varchar(32)");
        groupsDiffer(C_NAME, O_NAME);
        groupsMatch(C_NAME, A_NAME);
        groupsMatch(O_NAME, I_NAME);
    }

    @Test
    public void setDataType_I_oid() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE " + I_TABLE + " ALTER COLUMN oid SET DATA TYPE varchar(32)");
        groupsDiffer(O_NAME, I_NAME);
        groupsMatch(C_NAME, A_NAME, O_NAME);
    }

    //
    // DROP COLUMN <parent pk>
    //

    @Test
    public void dropColumn_C_id() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE "+C_TABLE+" DROP COLUMN id");
        groupsDiffer(C_NAME, A_NAME, O_NAME, I_NAME);
        groupsDiffer(A_NAME, O_NAME, I_NAME);
        groupsMatch(O_NAME, I_NAME);
    }

    @Test
    public void dropColumn_A_id() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE "+A_TABLE+" DROP COLUMN id");
        groupsMatch(C_NAME, A_NAME, O_NAME, I_NAME);
    }

    @Test
    public void dropColumn_O_id() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE "+O_TABLE+" DROP COLUMN id");
        groupsDiffer(I_NAME, C_NAME, A_NAME);
        groupsMatch(C_NAME, A_NAME, O_NAME);
    }

    @Test
    public void dropColumn_I_id() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE "+I_TABLE+" DROP COLUMN id");
        groupsMatch(C_NAME, A_NAME, O_NAME, I_NAME);
    }

    //
    // DROP COLUMN <child fk>
    //

    @Test
    public void dropColumn_A_cid() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE " + A_TABLE + " DROP COLUMN cid");
        groupsDiffer(C_NAME, A_NAME);
        groupsMatch(C_NAME, O_NAME, I_NAME);
    }

    @Test
    public void dropColumn_O_cid() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE " + O_TABLE + " DROP COLUMN cid");
        groupsDiffer(C_NAME, O_NAME);
        groupsMatch(C_NAME, A_NAME);
        groupsMatch(O_NAME, I_NAME);
    }

    @Test
    public void dropColumn_I_oid() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE " + I_TABLE + " DROP COLUMN oid");
        groupsDiffer(O_NAME, I_NAME);
        groupsMatch(C_NAME, A_NAME,  O_NAME);
    }

    //
    // ADD PRIMARY KEY
    //

    @Test
    public void addPrimaryKey_I() {
        createAndLoadCAOI_PK(true, true, true, false);
        runAlter("ALTER TABLE " + I_TABLE + " ADD PRIMARY KEY(id)");
        groupsMatch(C_NAME, A_NAME,  O_NAME, I_NAME);
    }

    //
    // DROP PRIMARY KEY
    //

    @Test
    public void dropPrimaryKey_C() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE "+C_TABLE+" DROP PRIMARY KEY");
        groupsDiffer(C_NAME, A_NAME, O_NAME, I_NAME);
        groupsDiffer(A_NAME, O_NAME, I_NAME);
        groupsMatch(O_NAME, I_NAME);
    }

    @Test
    public void dropPrimaryKey_A() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE "+A_TABLE+" DROP PRIMARY KEY");
        groupsMatch(C_NAME, A_NAME, O_NAME, I_NAME);
    }

    @Test
    public void dropPrimaryKey_O() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE "+O_TABLE+" DROP PRIMARY KEY");
        groupsDiffer(I_NAME, C_NAME, A_NAME);
        groupsMatch(C_NAME, A_NAME, O_NAME);
    }

    @Test
    public void dropPrimaryKey_I() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE "+I_TABLE+" DROP PRIMARY KEY");
        groupsMatch(C_NAME, A_NAME, O_NAME, I_NAME);
    }

    //
    // ADD GROUPING FOREIGN KEY
    //

    @Test
    public void addGroupingForeignKey_C() {
        int xid = createTable(X_NAME, "id varchar(5) not null primary key");
        writeRows(
                createNewRow(xid, "1"), // Adopts 1 (well formed group)
                                        // Leave 2 orphan
                createNewRow(xid, "4"), // Adopts 2 (c has no children)
                createNewRow(xid, "5")  // No Children
        );
        createAndLoadCAOI_FK(true, true, true);
        runAlter("ALTER TABLE " + C_TABLE + " ADD GROUPING FOREIGN KEY(cc) REFERENCES x(id)");
        groupsMatch(X_NAME, C_NAME, A_NAME, O_NAME, I_NAME);
    }

    @Test
    public void addGroupingForeignKey_A() {
        createAndLoadCAOI_FK(false, true, true);
        runAlter("ALTER TABLE " + A_TABLE + " ADD GROUPING FOREIGN KEY(cid) REFERENCES c(id)");
        groupsMatch(C_NAME, A_NAME, O_NAME, I_NAME);
    }

    @Test
    public void addGroupingForeignKey_O() {
        createAndLoadCAOI_FK(true, false, true);
        runAlter("ALTER TABLE " + O_TABLE + " ADD GROUPING FOREIGN KEY(cid) REFERENCES c(id)");
        groupsMatch(C_NAME, A_NAME, O_NAME, I_NAME);
    }

    @Test
    public void addGroupingForeignKey_I() {
        createAndLoadCAOI_FK(true, true, false);
        runAlter("ALTER TABLE "+I_TABLE+" ADD GROUPING FOREIGN KEY(oid) REFERENCES o(id)");
        groupsMatch(C_NAME, A_NAME, O_NAME, I_NAME);
    }

    //
    // DROP GROUPING FOREIGN KEY
    //

    @Test
    public void dropGroupingForeignKey_A() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE "+A_TABLE+" DROP GROUPING FOREIGN KEY");
        groupsDiffer(C_NAME, A_NAME);
        groupsMatch(C_NAME, O_NAME, I_NAME);
    }

    @Test
    public void dropGroupingForeignKey_O() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE "+O_TABLE+" DROP GROUPING FOREIGN KEY");
        groupsDiffer(C_NAME, O_NAME);
        groupsMatch(O_NAME, I_NAME);
        groupsMatch(C_NAME, A_NAME);
    }

    @Test
    public void dropGroupingForeignKey_I() {
        createAndLoadCAOI();
        runAlter("ALTER TABLE " + I_TABLE + " DROP GROUPING FOREIGN KEY");
        groupsDiffer(O_NAME, I_NAME);
        groupsMatch(C_NAME, A_NAME, O_NAME);
    }

    //
    // RENAME TABLE
    //

    @Test
    public void renameTable_C_X() {
        createAndLoadCAOI();
        runRenameTable(C_NAME, X_NAME);
        groupsMatch(X_NAME, A_NAME, O_NAME, I_NAME);
    }

    @Test
    public void renameTable_A_X() {
        createAndLoadCAOI();
        runRenameTable(A_NAME, X_NAME);
        groupsMatch(C_NAME, X_NAME, O_NAME, I_NAME);
    }

    @Test
    public void renameTable_O_X() {
        createAndLoadCAOI();
        runRenameTable(O_NAME, X_NAME);
        groupsMatch(C_NAME, A_NAME, X_NAME, I_NAME);
    }

    @Test
    public void renameTable_I_X() {
        createAndLoadCAOI();
        runRenameTable(I_NAME, X_NAME);
        groupsMatch(C_NAME, A_NAME, O_NAME, X_NAME);
    }

    //
    // RENAME COLUMN <parent pk>
    //

    @Test
    public void renameColumn_C_id() {
        createAndLoadCAOI();
        runRenameColumn(C_NAME, "id", "di");
        groupsMatch(X_NAME, A_NAME, O_NAME, I_NAME);
    }

    @Test
    public void renameColumn_A_id() {
        createAndLoadCAOI();
        runRenameColumn(A_NAME, "id", "di");
        groupsMatch(X_NAME, A_NAME, O_NAME, I_NAME);
    }

    @Test
    public void renameColumn_O_id() {
        createAndLoadCAOI();
        runRenameColumn(O_NAME, "id", "di");
        groupsMatch(X_NAME, A_NAME, O_NAME, I_NAME);
    }

    @Test
    public void renameColumn_I_id() {
        createAndLoadCAOI();
        runRenameColumn(I_NAME, "id", "di");
        groupsMatch(X_NAME, A_NAME, O_NAME, I_NAME);
    }

    //
    // RENAME COLUMN <child fk>
    //

    @Test
    public void renameColumn_A_cid() {
        createAndLoadCAOI();
        runRenameColumn(A_NAME, "cid", "dic");
        groupsMatch(X_NAME, A_NAME, O_NAME, I_NAME);
    }

    @Test
    public void renameColumn_O_cid() {
        createAndLoadCAOI();
        runRenameColumn(O_NAME, "cid", "dic");
        groupsMatch(X_NAME, A_NAME, O_NAME, I_NAME);
    }

    @Test
    public void renameColumn_I_oid() {
        createAndLoadCAOI();
        runRenameColumn(I_NAME, "oid", "dio");
        groupsMatch(X_NAME, A_NAME, O_NAME, I_NAME);
    }
}
