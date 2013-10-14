/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.foundationdb.server.manage;

import java.util.Collection;
import java.util.HashSet;

import com.foundationdb.ais.model.AkibanInformationSchema;
import com.foundationdb.ais.model.Index;
import com.foundationdb.ais.model.UserTable;
import com.foundationdb.sql.Main;
import com.foundationdb.server.service.dxl.DXLService;
import com.foundationdb.server.service.session.Session;
import com.foundationdb.server.service.session.SessionService;
import com.foundationdb.server.store.Store;

public class ManageMXBeanImpl implements ManageMXBean {
    private final Store store;
    private final DXLService dxlService;
    private final SessionService sessionService;

    public ManageMXBeanImpl(Store store, DXLService dxlService, SessionService sessionService) {
        this.store = store;
        this.dxlService = dxlService;
        this.sessionService = sessionService;
    }
    
    @Override
    public void ping() {
        return;
    }

    @Override
    public int getJmxPort() {
        return Integer.getInteger("com.sun.management.jmxremote.port", 0);
    }

    @Override
    public void buildIndexes(final String arg) {
        Session session = createSession();
        try {
            Collection<Index> indexes = gatherIndexes(session, arg);
            getStore().buildIndexes(session, indexes);
        } catch(Exception t) {
            throw new RuntimeException(t);
        } finally {
            session.close();
        }
    }

    @Override
    public void deleteIndexes(final String arg) {
        Session session = createSession();
        try {
            Collection<Index> indexes = gatherIndexes(session, arg);
            getStore().deleteIndexes(session, indexes);
        } catch(Exception t) {
            throw new RuntimeException(t);
        } finally {
            session.close();
        }
    }

    @Override
    public String getVersionString() {
        return Main.VERSION_INFO.versionLong;
    }

    private Store getStore() {
        return store;
    }

    private Session createSession() {
        return sessionService.createSession();
    }

    /**
     * Test if a given index is selected in the argument string. Format is:
     * <p><code>table=(table_name) index=(index_name)</code></p>
     * This can contain as many table=() and index=() segments as desired.
     * @param index Index to check
     * @param arg Index selection string as described above
     * @return
     */
    private boolean isIndexSelected(Index index, String arg) {
        return (!arg.contains("table=") ||
                arg.contains("table=(" +index.getIndexName().getTableName() + ")"))
               &&
               (!arg.contains("index=") ||
                arg.contains("index=(" + index.getIndexName().getName() + ")"));
    }

    /**
     * Create a collection of all indexes from all user tables in the current AIS
     * that are selected by arg.
     * @param session Session to use
     * @param arg Index selection string
     * @return Collection of selected Indexes
     */
    private Collection<Index> gatherIndexes(Session session, String arg) {
        AkibanInformationSchema ais = dxlService.ddlFunctions().getAIS(session);
        Collection<Index> indexes = new HashSet<>();
        for(UserTable table : ais.getUserTables().values()) {
            for(Index index : table.getIndexes()) {
                if(isIndexSelected(index, arg)) {
                    indexes.add(index);
                }
            }
        }
        return indexes;
    }

}
