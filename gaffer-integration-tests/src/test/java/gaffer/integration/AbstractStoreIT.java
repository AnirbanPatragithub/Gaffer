/*
 * Copyright 2016 Crown Copyright
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
package gaffer.integration;

import static org.junit.Assume.assumeTrue;

import gaffer.commonutil.TestGroups;
import gaffer.commonutil.TestPropertyNames;
import gaffer.commonutil.TestTypes;
import gaffer.data.element.Edge;
import gaffer.data.element.Entity;
import gaffer.function.simple.aggregate.Max;
import gaffer.function.simple.aggregate.StringConcat;
import gaffer.function.simple.aggregate.Sum;
import gaffer.graph.Graph;
import gaffer.operation.OperationException;
import gaffer.operation.data.EdgeSeed;
import gaffer.operation.data.ElementSeed;
import gaffer.operation.data.EntitySeed;
import gaffer.operation.impl.add.AddElements;
import gaffer.serialisation.implementation.JavaSerialiser;
import gaffer.serialisation.simple.IntegerSerialiser;
import gaffer.serialisation.simple.LongSerialiser;
import gaffer.serialisation.simple.StringSerialiser;
import gaffer.store.StoreProperties;
import gaffer.store.StoreTrait;
import gaffer.store.schema.Schema;
import gaffer.store.schema.SchemaEdgeDefinition;
import gaffer.store.schema.SchemaEntityDefinition;
import gaffer.store.schema.TypeDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Logic/config for setting up and running store integration tests.
 * All tests will be skipped if the storeProperties variable has not been set
 * prior to running the tests.
 */
public abstract class AbstractStoreIT {
    // Identifier prefixes
    protected static final String SOURCE = "source";
    protected static final String DEST = "dest";
    protected static final String SOURCE_DIR = "sourceDir";
    protected static final String DEST_DIR = "destDir";
    protected static final String A = "A";
    protected static final String B = "B";
    protected static final String C = "C";
    protected static final String D = "D";
    protected static final String[] VERTEX_PREFIXES = new String[]{A, B, C, D};

    // Identifiers
    protected static final String SOURCE_1 = SOURCE + 1;
    protected static final String DEST_1 = DEST + 1;

    protected static final String SOURCE_2 = SOURCE + 2;
    protected static final String DEST_2 = DEST + 2;

    protected static final String SOURCE_3 = SOURCE + 3;
    protected static final String DEST_3 = DEST + 3;

    protected static final String SOURCE_DIR_1 = SOURCE_DIR + 1;
    protected static final String DEST_DIR_1 = DEST_DIR + 1;

    protected static final String SOURCE_DIR_2 = SOURCE_DIR + 2;
    protected static final String DEST_DIR_2 = DEST_DIR + 2;

    protected static final String SOURCE_DIR_3 = SOURCE_DIR + 3;
    protected static final String DEST_DIR_3 = DEST_DIR + 3;

    protected static Graph graph;
    private static Schema storeSchema = new Schema();
    private static StoreProperties storeProperties;

    private final Map<EntitySeed, Entity> entities = createEntities();
    private final Map<EdgeSeed, Edge> edges = createEdges();

    @Rule
    public TestName name = new TestName();


    public static void setStoreProperties(final StoreProperties storeProperties) {
        AbstractStoreIT.storeProperties = storeProperties;
    }

    public static StoreProperties getStoreProperties() {
        return storeProperties;
    }

    public static Schema getStoreSchema() {
        return storeSchema;
    }

    public static void setStoreSchema(final Schema storeSchema) {
        AbstractStoreIT.storeSchema = storeSchema;
    }

    /**
     * Setup the Parameterised Graph for each type of Store.
     * Excludes tests where the graph's Store doesn't implement the required StoreTraits.
     *
     * @throws Exception should never be thrown
     */
    @Before
    public void setup() throws Exception {
        assumeTrue("Skipping test as no store properties have been defined.", null != storeProperties);

        graph = new Graph.Builder()
                .storeProperties(storeProperties)
                .addSchema(createSchema())
                .addSchema(storeSchema)
                .build();

        final String originalMethodName = name.getMethodName().endsWith("]")
                ? name.getMethodName().substring(0, name.getMethodName().indexOf("["))
                : name.getMethodName();
        final Method testMethod = this.getClass().getMethod(originalMethodName);
        final Collection<StoreTrait> requiredTraits = new ArrayList<>();

        for (Annotation annotation : testMethod.getDeclaredAnnotations()) {
            if (annotation.annotationType().equals(gaffer.integration.TraitRequirement.class)) {
                final gaffer.integration.TraitRequirement traitRequirement = (gaffer.integration.TraitRequirement) annotation;
                requiredTraits.addAll(Arrays.asList(traitRequirement.value()));
            }
        }

        for (StoreTrait requiredTrait : requiredTraits) {
            assumeTrue("Skipping test as the store does not implement all required traits.", graph.hasTrait(requiredTrait));
        }
    }

    protected Schema createSchema() {
        return new Schema.Builder()
                .type(TestTypes.ID_STRING, new TypeDefinition.Builder()
                        .clazz(String.class)
                        .build())
                .type(TestTypes.DIRECTED_EITHER, new TypeDefinition.Builder()
                        .clazz(Boolean.class)
                        .build())
                .type(TestTypes.PROP_STRING, new TypeDefinition.Builder()
                        .clazz(String.class)
                        .aggregateFunction(new StringConcat())
                        .serialiser(new JavaSerialiser())
                        .build())
                .type(TestTypes.PROP_INTEGER, new TypeDefinition.Builder()
                        .clazz(Integer.class)
                        .aggregateFunction(new Max())
                        .serialiser(new IntegerSerialiser())
                        .build())
                .type(TestTypes.PROP_COUNT, new TypeDefinition.Builder()
                        .clazz(Long.class)
                        .aggregateFunction(new Sum())
                        .serialiser(new LongSerialiser())
                        .build())
                .entity(TestGroups.ENTITY, new SchemaEntityDefinition.Builder()
                        .vertex(TestTypes.ID_STRING)
                        .property(TestPropertyNames.STRING, TestTypes.PROP_STRING)
                        .build())
                .edge(TestGroups.EDGE, new SchemaEdgeDefinition.Builder()
                        .source(TestTypes.ID_STRING)
                        .destination(TestTypes.ID_STRING)
                        .directed(TestTypes.DIRECTED_EITHER)
                        .property(TestPropertyNames.INT, TestTypes.PROP_INTEGER)
                        .property(TestPropertyNames.COUNT, TestTypes.PROP_COUNT)
                        .build())
                .vertexSerialiser(new StringSerialiser())
                .build();
    }

    @After
    public void tearDown() {
        graph = null;
    }

    public void addDefaultElements() throws OperationException {
        graph.execute(new AddElements.Builder()
                .elements((Iterable) getEntities().values())
                .build());

        graph.execute(new AddElements.Builder()
                .elements((Iterable) getEdges().values())
                .build());
    }

    public Map<EntitySeed, Entity> getEntities() {
        return entities;
    }

    public Map<EdgeSeed, Edge> getEdges() {
        return edges;
    }

    public Entity getEntity(final Object vertex) {
        return entities.get(new EntitySeed(vertex));
    }

    public Edge getEdge(final Object source, final Object dest, final boolean isDirected) {
        return edges.get(new EdgeSeed(source, dest, isDirected));
    }

    protected Map<EdgeSeed, Edge> createEdges() {
        final Map<EdgeSeed, Edge> edges = new HashMap<>();
        Edge edge;
        for (int i = 0; i <= 10; i++) {
            for (int j = 0; j < VERTEX_PREFIXES.length; j++) {
                edge = new Edge(TestGroups.EDGE, VERTEX_PREFIXES[0] + i, VERTEX_PREFIXES[j] + i, false);
                edge.putProperty(TestPropertyNames.INT, j);
                edge.putProperty(TestPropertyNames.COUNT, 1L);
                addToMap(edge, edges);
            }

            edge = new Edge(TestGroups.EDGE, SOURCE + i, DEST + i, false);
            edge.putProperty(TestPropertyNames.INT, i);
            edge.putProperty(TestPropertyNames.COUNT, 1L);
            addToMap(edge, edges);

            edge = new Edge(TestGroups.EDGE, SOURCE_DIR + i, DEST_DIR + i, true);
            edge.putProperty(TestPropertyNames.INT, i);
            edge.putProperty(TestPropertyNames.COUNT, 1L);
            addToMap(edge, edges);
        }

        return edges;
    }

    protected Map<EntitySeed, Entity> createEntities() {
        final Map<EntitySeed, Entity> entities = new HashMap<>();
        Entity entity;
        for (int i = 0; i <= 10; i++) {
            final String prop = String.valueOf(i);
            for (int j = 0; j < VERTEX_PREFIXES.length; j++) {
                entity = new Entity(TestGroups.ENTITY, VERTEX_PREFIXES[j] + i);
                entity.putProperty(TestPropertyNames.STRING, String.valueOf(j));
                addToMap(entity, entities);
            }

            entity = new Entity(TestGroups.ENTITY, SOURCE + i);
            entity.putProperty(TestPropertyNames.STRING, prop);
            addToMap(entity, entities);

            entity = new Entity(TestGroups.ENTITY, DEST + i);
            entity.putProperty(TestPropertyNames.STRING, prop);
            addToMap(entity, entities);

            entity = new Entity(TestGroups.ENTITY, SOURCE_DIR + i);
            entity.putProperty(TestPropertyNames.STRING, prop);
            addToMap(entity, entities);

            entity = new Entity(TestGroups.ENTITY, DEST_DIR + i);
            entity.putProperty(TestPropertyNames.STRING, prop);
            addToMap(entity, entities);
        }

        return entities;
    }

    protected void addToMap(final Edge element, final Map<EdgeSeed, Edge> edges) {
        edges.put(ElementSeed.createSeed(element), element);
    }

    protected void addToMap(final Entity element, final Map<EntitySeed, Entity> entities) {
        entities.put(ElementSeed.createSeed(element), element);
    }
}
