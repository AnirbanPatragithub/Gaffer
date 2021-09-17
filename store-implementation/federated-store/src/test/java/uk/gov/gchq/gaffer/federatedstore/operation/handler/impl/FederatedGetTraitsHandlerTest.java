/*
 * Copyright 2018-2021 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.gchq.gaffer.federatedstore.operation.handler.impl;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.gov.gchq.gaffer.accumulostore.AccumuloProperties;
import uk.gov.gchq.gaffer.cache.CacheServiceLoader;
import uk.gov.gchq.gaffer.commonutil.StreamUtil;
import uk.gov.gchq.gaffer.commonutil.iterable.CloseableIterable;
import uk.gov.gchq.gaffer.data.element.Element;
import uk.gov.gchq.gaffer.data.element.id.EntityId;
import uk.gov.gchq.gaffer.federatedstore.FederatedStore;
import uk.gov.gchq.gaffer.federatedstore.FederatedStoreConstants;
import uk.gov.gchq.gaffer.federatedstore.FederatedStoreProperties;
import uk.gov.gchq.gaffer.federatedstore.operation.AddGraph;
import uk.gov.gchq.gaffer.operation.impl.add.AddElements;
import uk.gov.gchq.gaffer.operation.impl.get.GetAdjacentIds;
import uk.gov.gchq.gaffer.operation.impl.get.GetAllElements;
import uk.gov.gchq.gaffer.operation.impl.get.GetElements;
import uk.gov.gchq.gaffer.serialisation.Serialiser;
import uk.gov.gchq.gaffer.store.Context;
import uk.gov.gchq.gaffer.store.Store;
import uk.gov.gchq.gaffer.store.StoreProperties;
import uk.gov.gchq.gaffer.store.StoreTrait;
import uk.gov.gchq.gaffer.store.library.HashMapGraphLibrary;
import uk.gov.gchq.gaffer.store.operation.GetTraits;
import uk.gov.gchq.gaffer.store.operation.handler.OperationHandler;
import uk.gov.gchq.gaffer.store.operation.handler.OutputOperationHandler;
import uk.gov.gchq.gaffer.store.schema.Schema;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.gchq.gaffer.store.StoreTrait.MATCHED_VERTEX;
import static uk.gov.gchq.gaffer.store.StoreTrait.POST_AGGREGATION_FILTERING;
import static uk.gov.gchq.gaffer.store.StoreTrait.POST_TRANSFORMATION_FILTERING;
import static uk.gov.gchq.gaffer.store.StoreTrait.PRE_AGGREGATION_FILTERING;
import static uk.gov.gchq.gaffer.store.StoreTrait.TRANSFORMATION;
import static uk.gov.gchq.gaffer.user.StoreUser.testUser;

public class FederatedGetTraitsHandlerTest {
    public static final String ALT_STORE = "altStore";
    public static final String FED_STORE_ID = "fedStoreId";
    public static final String ACC_STORE = "accStore";
    private StoreProperties storeProperties;
    private FederatedStore federatedStore;
    private FederatedStoreProperties properties;

    private static final AccumuloProperties PROPERTIES = AccumuloProperties.loadStoreProperties(StreamUtil.openStream(FederatedGetTraitsHandlerTest.class, "/properties/singleUseAccumuloStore.properties"));

    @BeforeEach
    public void setUp() throws Exception {
        federatedStore = new FederatedStore();
        properties = new FederatedStoreProperties();
        HashMapGraphLibrary.clear();
        CacheServiceLoader.shutdown();
        storeProperties = new StoreProperties();
        storeProperties.setStoreClass(TestStoreImpl.class);
    }

    @Test
    public void shouldGetZeroTraitsForEmptyStore() throws Exception {
        // Given
        federatedStore.initialise(FED_STORE_ID, null, properties);

        // When
        final Set<StoreTrait> traits = federatedStore.execute(
                new GetTraits.Builder()
                        .currentTraits(false)
                        .build(),
                new Context(testUser()));

        // Then
        assertEquals(Collections.emptySet(), traits);
    }

    @Test
    public void shouldGetZeroTraitsForEmptyStoreWithCurrentTraits() throws Exception {
        // Given
        federatedStore.initialise(FED_STORE_ID, null, properties);
        assertEquals(0, federatedStore.getAllGraphIds(testUser()).size(),
                "graph is not starting empty");

        // When
        final Set<StoreTrait> traits = federatedStore.execute(new GetTraits.Builder()
                .currentTraits(true)
                .build(), new Context(testUser()));

        // Then
        assertEquals(Collections.emptySet(), traits);
    }

    @Test
    public void shouldGetAllTraitsWhenContainsStoreWithOtherTraits() throws Exception {
        // Given
        federatedStore.initialise(FED_STORE_ID, null, properties);
        federatedStore.execute(new AddGraph.Builder()
                .isPublic(true)
                .graphId(ALT_STORE)
                .storeProperties(storeProperties)
                .schema(new Schema())
                .build(), new Context(testUser()));

        StoreProperties altProps = new StoreProperties();
        altProps.setStoreClass(TestStoreAltImpl.class);
        federatedStore.execute(new AddGraph.Builder()
                .isPublic(true)
                .graphId(ALT_STORE + 2)
                .storeProperties(altProps)
                .schema(new Schema())
                .build(), new Context(testUser()));

        // When
        final Set<StoreTrait> traits = federatedStore.execute(
                new GetTraits.Builder()
                        .currentTraits(false)
                        .build(),
                new Context(testUser()));

        HashSet<Object> expectedIntersectionTraits = new HashSet<>();
        expectedIntersectionTraits.addAll(TestStoreImpl.STORE_TRAITS);
        expectedIntersectionTraits.retainAll(TestStoreAltImpl.STORE_TRAITS);

        // Then
        assertEquals(expectedIntersectionTraits, traits);
        assertTrue(expectedIntersectionTraits.size() < TestStoreImpl.STORE_TRAITS.size());
    }

    @Test
    public void shouldGetCurrentTraitsWhenContainsStoreWithOtherTraits() throws Exception {
        // Given
        federatedStore.initialise(FED_STORE_ID, null, properties);
        federatedStore.execute(new AddGraph.Builder()
                .isPublic(true)
                .graphId(ALT_STORE)
                .storeProperties(storeProperties)
                .schema(new Schema())
                .build(), new Context(testUser()));

        // When
        final Set<StoreTrait> traits = federatedStore.execute(
                new GetTraits.Builder()
                        .currentTraits(true)
                        .build(),
                new Context(testUser()));

        // Then
        assertEquals(
                Sets.newHashSet(
                        TRANSFORMATION,
                        MATCHED_VERTEX,
                        PRE_AGGREGATION_FILTERING,
                        POST_AGGREGATION_FILTERING,
                        POST_TRANSFORMATION_FILTERING
                ),
                traits);
    }

    @Test
    public void shouldGetCurrentTraitsWhenContainsStoreWithOtherTraitsWithOptions() throws Exception {
        // Given
        federatedStore.initialise(FED_STORE_ID, null, properties);

        federatedStore.execute(new AddGraph.Builder()
                .isPublic(true)
                .graphId(ALT_STORE)
                .storeProperties(storeProperties)
                .schema(new Schema())
                .build(), new Context(testUser()));

        federatedStore.execute(new AddGraph.Builder()
                .isPublic(true)
                .graphId(ACC_STORE)
                .storeProperties(PROPERTIES)
                .schema(new Schema())
                .build(), new Context(testUser()));

        // When
        final Set<StoreTrait> traits = federatedStore.execute(
                new GetTraits.Builder()
                        .option(FederatedStoreConstants.KEY_OPERATION_OPTIONS_GRAPH_IDS, ALT_STORE)
                        .currentTraits(true)
                        .build(),
                new Context(testUser()));

        // Then
        assertEquals(
                Sets.newHashSet(
                        TRANSFORMATION,
                        MATCHED_VERTEX,
                        PRE_AGGREGATION_FILTERING,
                        POST_AGGREGATION_FILTERING,
                        POST_TRANSFORMATION_FILTERING
                ),
                traits);
    }

    @Test
    public void shouldGetAllTraitsWhenContainsStoreWithOtherTraitsWithOptions() throws Exception {
        // Given
        federatedStore.initialise(FED_STORE_ID, null, properties);

        federatedStore.execute(new AddGraph.Builder()
                .isPublic(true)
                .graphId(ALT_STORE)
                .storeProperties(storeProperties)
                .schema(new Schema())
                .build(), new Context(testUser()));

        federatedStore.execute(new AddGraph.Builder()
                .isPublic(true)
                .graphId(ACC_STORE)
                .storeProperties(PROPERTIES)
                .schema(new Schema())
                .build(), new Context(testUser()));

        // When
        final Set<StoreTrait> traits = federatedStore.execute(
                new GetTraits.Builder()
                        .option(FederatedStoreConstants.KEY_OPERATION_OPTIONS_GRAPH_IDS, ALT_STORE)
                        .currentTraits(false)
                        .build(),
                new Context(testUser()));

        // Then
        assertEquals(TestStoreImpl.STORE_TRAITS, traits);
    }

    public static class TestStoreImpl extends Store {

        private static final Set<StoreTrait> STORE_TRAITS = ImmutableSet.of(
                StoreTrait.INGEST_AGGREGATION,
                StoreTrait.PRE_AGGREGATION_FILTERING,
                StoreTrait.POST_AGGREGATION_FILTERING,
                StoreTrait.TRANSFORMATION,
                StoreTrait.POST_TRANSFORMATION_FILTERING,
                StoreTrait.MATCHED_VERTEX);

        @Override
        public Set<StoreTrait> getTraits() {
            return STORE_TRAITS;
        }

        @Override
        protected void addAdditionalOperationHandlers() {

        }

        @Override
        protected OutputOperationHandler<GetElements, CloseableIterable<? extends Element>> getGetElementsHandler() {
            return null;
        }

        @Override
        protected OutputOperationHandler<GetAllElements, CloseableIterable<? extends Element>> getGetAllElementsHandler() {
            return null;
        }

        @Override
        protected OutputOperationHandler<? extends GetAdjacentIds, CloseableIterable<? extends EntityId>> getAdjacentIdsHandler() {
            return null;
        }

        @Override
        protected OperationHandler<? extends AddElements> getAddElementsHandler() {
            return null;
        }

        @Override
        protected Class<? extends Serialiser> getRequiredParentSerialiserClass() {
            return null;
        }
    }

    public static class TestStoreAltImpl extends TestStoreImpl {

        private static final Set<StoreTrait> STORE_TRAITS = ImmutableSet.of(StoreTrait.VISIBILITY);

        @Override
        public Set<StoreTrait> getTraits() {
            return STORE_TRAITS;
        }
    }

}
