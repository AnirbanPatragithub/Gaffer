/*
 * Copyright 2018 Crown Copyright
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

package uk.gov.gchq.gaffer.store.operation.handler;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import uk.gov.gchq.gaffer.operation.OperationException;
import uk.gov.gchq.gaffer.operation.impl.SetVariable;
import uk.gov.gchq.gaffer.store.AbstractStore;
import uk.gov.gchq.gaffer.store.Context;
import uk.gov.gchq.gaffer.user.User;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class SetVariableHandlerTest {

    @Test
    public void shouldSetVariableInContext() throws OperationException {
        // Given
        final Context context = new Context(new User());
        final AbstractStore store = mock(AbstractStore.class);
        final String testVarName = "testVarName";
        final int testVarValue = 4;

        SetVariableHandler handler = new SetVariableHandler();
        SetVariable op = new SetVariable.Builder().variableName(testVarName).input(testVarValue).build();

        // When
        handler.doOperation(op, context, store);

        // Then
        assertTrue(context.getVariable(testVarName).equals(testVarValue));
        assertTrue(context.getVariables().equals(ImmutableMap.of(testVarName, testVarValue)));
    }
}