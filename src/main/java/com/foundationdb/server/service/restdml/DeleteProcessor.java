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
package com.foundationdb.server.service.restdml;

import java.util.List;

import com.foundationdb.server.types.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.foundationdb.ais.model.AkibanInformationSchema;
import com.foundationdb.ais.model.CacheValueGenerator;
import com.foundationdb.ais.model.Index;
import com.foundationdb.ais.model.TableName;
import com.foundationdb.qp.operator.API;
import com.foundationdb.qp.operator.Cursor;
import com.foundationdb.qp.operator.Operator;
import com.foundationdb.qp.row.Row;
import com.foundationdb.server.service.session.Session;
import com.foundationdb.server.store.Store;
import com.foundationdb.server.types.service.TypesRegistryService;

public class DeleteProcessor extends DMLProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteProcessor.class);
    private DeleteGenerator deleteGenerator;
    
    public DeleteProcessor (
            Store store,
            TypesRegistryService typesRegistryService) {
        super (store, typesRegistryService);
    }

    private static final CacheValueGenerator<DeleteGenerator> CACHED_DELETE_GENERATOR =
            new CacheValueGenerator<DeleteGenerator>() {
                @Override
                public DeleteGenerator valueFor(AkibanInformationSchema ais) {
                    return new DeleteGenerator(ais);
                }
            };

    public void processDelete (Session session, AkibanInformationSchema ais, TableName tableName, String identifiers) {
        ProcessContext context = new ProcessContext (ais, session, tableName);
        
        deleteGenerator = getGenerator (CACHED_DELETE_GENERATOR, context);

        Index pkIndex = context.table.getPrimaryKeyIncludingInternal().getIndex();
        List<List<String>> pks = PrimaryKeyParser.parsePrimaryKeys(identifiers, pkIndex);
        
        Value value = new Value(context.typesTranslator.typeForString());
        Cursor cursor = null;

        try {
            Operator delete = deleteGenerator.create(tableName);
            cursor = API.cursor(delete, context.queryContext, context.queryBindings);

            for (List<String> key : pks) {
                for (int i = 0; i < key.size(); i++) {
                    String akey = key.get(i);
                    value.putString(akey, null);
                    context.queryBindings.setValue(i, value);
                }
    
                cursor.openTopLevel();
                Row row;
                while ((row = cursor.next()) != null) {
                    // Do Nothing - the act of reading the cursor 
                    // does the delete row processing.
                    // TODO: Check that we got 1 row through.
                }
                cursor.closeTopLevel();
            }
        } finally {
            if (cursor != null)
                cursor.destroy();

        }
    }
}
