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

package uk.gov.gchq.gaffer.data.elementdefinition.view;

import org.junit.Test;
import uk.gov.gchq.gaffer.commonutil.JsonUtil;
import uk.gov.gchq.gaffer.commonutil.TestGroups;
import uk.gov.gchq.gaffer.commonutil.TestPropertyNames;
import uk.gov.gchq.gaffer.data.element.function.ElementFilter;
import uk.gov.gchq.gaffer.data.element.function.ElementTransformer;
import uk.gov.gchq.gaffer.function.ExampleFilterFunction;
import uk.gov.gchq.gaffer.function.ExampleTransformFunction;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ViewTest {

    @Test
    public void shouldCreateEmptyViewWithBasicConstructor() {
        //Given

        //When
        View view = new View();

        //Then
        assertTrue(view.getEdges().isEmpty());
        assertTrue(view.getEntities().isEmpty());
    }

    @Test
    public void shouldCreateNewViewWithEdgeAndEntityGroups() {
        //Given
        List<String> entityGroups = new ArrayList<>();
        List<String> edgeGroups = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            entityGroups.add(TestGroups.ENTITY + i);
            edgeGroups.add(TestGroups.EDGE + i);
        }

        //When
        View view = new View.Builder()
                .entities(entityGroups)
                .edges(edgeGroups)
                .build();

        //Then
        assertTrue(view.getEntityGroups().containsAll(entityGroups));
        assertEquals(entityGroups.size(), view.getEntityGroups().size());
        assertTrue(view.getEdgeGroups().containsAll(edgeGroups));
        assertEquals(edgeGroups.size(), view.getEdgeGroups().size());
    }

    @Test
    public void shouldBuildView() {
        // Given
        final ViewElementDefinition edgeDef1 = new ViewElementDefinition();
        final ViewElementDefinition edgeDef2 = new ViewElementDefinition();
        final ViewElementDefinition entityDef1 = new ViewElementDefinition();
        final ViewElementDefinition entityDef2 = new ViewElementDefinition();

        // When
        final View view = new View.Builder()
                .edge(TestGroups.EDGE, edgeDef1)
                .entity(TestGroups.ENTITY, entityDef1)
                .entity(TestGroups.ENTITY_2, entityDef2)
                .edge(TestGroups.EDGE_2, edgeDef2)
                .build();

        // Then
        assertEquals(2, view.getEdges().size());
        assertSame(edgeDef1, view.getEdge(TestGroups.EDGE));
        assertSame(edgeDef2, view.getEdge(TestGroups.EDGE_2));

        assertEquals(2, view.getEntities().size());
        assertSame(entityDef1, view.getEntity(TestGroups.ENTITY));
        assertSame(entityDef2, view.getEntity(TestGroups.ENTITY_2));
    }

    @Test
    public void shouldSerialiseToJson() {
        // Given
        final View view = new View.Builder()
                .edge(TestGroups.EDGE, new ViewElementDefinition.Builder()
                        .transientProperty(TestPropertyNames.PROP_3, String.class)
                        .transformer(new ElementTransformer.Builder()
                                .select(TestPropertyNames.PROP_1, TestPropertyNames.PROP_2)
                                .project(TestPropertyNames.PROP_3)
                                .execute(new ExampleTransformFunction())
                                .build())
                        .postTransformFilter(new ElementFilter.Builder()
                                .select(TestPropertyNames.PROP_3)
                                .execute(new ExampleFilterFunction())
                                .build())
                        .build())
                .entity(TestGroups.ENTITY, new ViewElementDefinition.Builder()
                        .preAggregationFilter(new ElementFilter.Builder()
                                .select(TestPropertyNames.PROP_1)
                                .execute(new ExampleFilterFunction())
                                .build())
                        .build())
                .build();

        // When
        byte[] json = view.toJson(true);

        // Then
        JsonUtil.assertEquals(String.format("{%n" +
                "  \"edges\" : {%n" +
                "    \"BasicEdge\" : {%n" +
                "      \"transientProperties\" : {%n" +
                "        \"property3\" : \"java.lang.String\"%n" +
                "      },%n" +
                "      \"postTransformFilterFunctions\" : [ {%n" +
                "        \"function\" : {%n" +
                "          \"class\" : \"uk.gov.gchq.gaffer.function.ExampleFilterFunction\"%n" +
                "        },%n" +
                "        \"selection\" : [ \"property3\" ]%n" +
                "      } ],%n" +
                "      \"transformFunctions\" : [ {%n" +
                "        \"function\" : {%n" +
                "          \"class\" : \"uk.gov.gchq.gaffer.function.ExampleTransformFunction\"%n" +
                "        },%n" +
                "        \"selection\" : [ \"property1\", \"property2\" ],%n" +
                "        \"projection\" : [ \"property3\" ]%n" +
                "      } ]%n" +
                "    }%n" +
                "  },%n" +
                "  \"entities\" : {%n" +
                "    \"BasicEntity\" : {%n" +
                "      \"transientProperties\" : { },%n" +
                "      \"preAggregationFilterFunctions\" : [ {%n" +
                "        \"function\" : {%n" +
                "          \"class\" : \"uk.gov.gchq.gaffer.function.ExampleFilterFunction\"%n" +
                "        },%n" +
                "        \"selection\" : [ \"property1\" ]%n" +
                "      } ]%n" +
                "    }%n" +
                "  }%n" +
                "}"), new String(json));
    }

    @Test
    public void shouldJsonSerialiseAndDeserialise() {
        // Given
        final View view = new View.Builder()
                .edge(TestGroups.EDGE, new ViewElementDefinition.Builder()
                        .transientProperty(TestPropertyNames.PROP_3, String.class)
                        .transformer(new ElementTransformer.Builder()
                                .select(TestPropertyNames.PROP_1, TestPropertyNames.PROP_2)
                                .project(TestPropertyNames.PROP_3)
                                .execute(new ExampleTransformFunction())
                                .build())
                        .postTransformFilter(new ElementFilter.Builder()
                                .select(TestPropertyNames.PROP_3)
                                .execute(new ExampleFilterFunction())
                                .build())
                        .build())
                .entity(TestGroups.ENTITY, new ViewElementDefinition.Builder()
                        .preAggregationFilter(new ElementFilter.Builder()
                                .select(TestPropertyNames.PROP_1)
                                .execute(new ExampleFilterFunction())
                                .build())
                        .build())
                .build();

        // When
        byte[] json = view.toJson(true);
        final View deserialisedView = new View.Builder().json(json).build();

        // Then
        assertEquals(1, deserialisedView.getEntityGroups().size());
        final ViewElementDefinition entityDef = deserialisedView.getEntity(TestGroups.ENTITY);
        assertTrue(entityDef.getTransientProperties().isEmpty());
        assertNull(entityDef.getTransformer());
        assertEquals(1, entityDef.getPreAggregationFilter().getFunctions().size());
        assertTrue(entityDef.getPreAggregationFilter().getFunctions().get(0).getFunction() instanceof ExampleFilterFunction);
        assertEquals(1, entityDef.getPreAggregationFilter().getFunctions().get(0).getSelection().size());
        assertEquals(TestPropertyNames.PROP_1, entityDef.getPreAggregationFilter().getFunctions().get(0).getSelection().get(0));

        final ViewElementDefinition edgeDef = deserialisedView.getEdge(TestGroups.EDGE);
        assertEquals(1, edgeDef.getTransientProperties().size());
        assertEquals(String.class, edgeDef.getTransientPropertyMap().get(TestPropertyNames.PROP_3));
        assertNull(edgeDef.getPreAggregationFilter());
        assertEquals(1, edgeDef.getTransformer().getFunctions().size());
        assertTrue(edgeDef.getTransformer().getFunctions().get(0).getFunction() instanceof ExampleTransformFunction);
        assertEquals(2, edgeDef.getTransformer().getFunctions().get(0).getSelection().size());
        assertEquals(TestPropertyNames.PROP_1, edgeDef.getTransformer().getFunctions().get(0).getSelection().get(0));
        assertEquals(TestPropertyNames.PROP_2, edgeDef.getTransformer().getFunctions().get(0).getSelection().get(1));
        assertEquals(1, edgeDef.getTransformer().getFunctions().get(0).getProjection().size());
        assertEquals(TestPropertyNames.PROP_3, edgeDef.getTransformer().getFunctions().get(0).getProjection().get(0));
        assertEquals(1, edgeDef.getPostTransformFilter().getFunctions().size());
        assertTrue(edgeDef.getPostTransformFilter().getFunctions().get(0).getFunction() instanceof ExampleFilterFunction);
        assertEquals(1, edgeDef.getPostTransformFilter().getFunctions().get(0).getSelection().size());
        assertEquals(TestPropertyNames.PROP_3, edgeDef.getPostTransformFilter().getFunctions().get(0).getSelection().get(0));
    }

    @Test
    public void shouldCreateAnIdenticalObjectWhenCloned() {
        // Given
        final ViewElementDefinition edgeDef1 = new ViewElementDefinition();
        final ViewElementDefinition edgeDef2 = new ViewElementDefinition();
        final ViewElementDefinition entityDef1 = new ViewElementDefinition();
        final ViewElementDefinition entityDef2 = new ViewElementDefinition();

        // When
        final View view = new View.Builder()
                .edge(TestGroups.EDGE, edgeDef1)
                .entity(TestGroups.ENTITY, entityDef1)
                .entity(TestGroups.ENTITY_2, entityDef2)
                .edge(TestGroups.EDGE_2, edgeDef2)
                .build();

        // Then
        final View clone = view.clone();

        // Check that the objects are equal
        assertEquals(view, clone);

        final byte[] viewJson = view.toCompactJson();
        final byte[] cloneJson = clone.toCompactJson();

        // Check that JSON representations of the objects are equal
        assertArrayEquals(viewJson, cloneJson);

        final View viewFromJson = new View.Builder().json(viewJson).build();
        final View cloneFromJson = new View.Builder().json(cloneJson).build();

        // Check that objects created from JSON representations are equal
        assertEquals(viewFromJson, cloneFromJson);

        // Check that objects created from JSON representations are equal
        assertEquals(viewFromJson, view);
        assertEquals(cloneFromJson, clone);
    }

    @Test
    public void shouldSerialiseToCompactJson() {
        // Given
        final View view = new View();

        // When
        final String compactJson = new String(view.toCompactJson());

        // Then - no description fields or new lines
        assertFalse(compactJson.contains(String.format("%n")));
    }

    @Test
    public void shouldMergeDifferentViews() {
        // Given
        final View view1 = new View.Builder()
                .entity(TestGroups.ENTITY)
                .edge(TestGroups.EDGE)
                .build();

        final View view2 = new View.Builder()
                .entity(TestGroups.ENTITY)
                .entity(TestGroups.ENTITY_2)
                .edge(TestGroups.EDGE)
                .edge(TestGroups.EDGE_2)
                .build();

        // When
        final View mergedView = new View.Builder()
                .merge(view1)
                .merge(view2)
                .build();

        // Then
        assertEquals(2, mergedView.getEntities().size());
        assertEquals(2, mergedView.getEdges().size());
    }
}
