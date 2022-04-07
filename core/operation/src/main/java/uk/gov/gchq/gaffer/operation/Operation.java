/*
 * Copyright 2016-2021 Crown Copyright
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

package uk.gov.gchq.gaffer.operation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.exception.CloneFailedException;

import uk.gov.gchq.gaffer.commonutil.CloseableUtil;
import uk.gov.gchq.gaffer.jsonserialisation.JSONSerialiser;
import uk.gov.gchq.gaffer.operation.serialisation.TypeReferenceImpl;
import uk.gov.gchq.koryphe.Since;
import uk.gov.gchq.koryphe.Summary;
import uk.gov.gchq.koryphe.serialisation.json.JsonSimpleClassName;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static uk.gov.gchq.gaffer.operation.OperationConstants.KEY_INPUT;
import static uk.gov.gchq.gaffer.operation.OperationConstants.KEY_OUTPUT_TYPE_REFERENCE;
import static uk.gov.gchq.gaffer.operation.OperationConstants.LOCALE;

/**
 * An {@code Operation} defines an operation to be processed on a graph.
 * All operations must to implement this interface.
 * Operations should be written to be as generic as possible to allow them to be applied to different graph/stores.
 * NOTE - operations should not contain the operation logic. The logic should be separated out into a operation handler.
 * This will allow you to execute the same operation on different stores with different handlers.
 * <p>
 * Operations must be JSON serialisable in order to make REST API calls.
 * </p>
 * <p>
 * Any fields that are required should be annotated with the {@link Required} annotation.
 * </p>
 * <p>
 * Operation implementations need to implement this Operation interface and any of the following interfaces they wish to make use of:
 * {@link uk.gov.gchq.gaffer.operation.io.Input}
 * {@link uk.gov.gchq.gaffer.operation.io.Output}
 * {@link uk.gov.gchq.gaffer.operation.io.InputOutput} (Use this instead of Input and Output if your operation takes both input and output.)
 * {@link uk.gov.gchq.gaffer.operation.io.MultiInput} (Use this in addition if you operation takes multiple inputs. This will help with json
 * serialisation)
 * {@link uk.gov.gchq.gaffer.operation.Validatable}
 * {@link uk.gov.gchq.gaffer.operation.graph.OperationView}
 * {@link uk.gov.gchq.gaffer.operation.graph.GraphFilters}
 * {@link uk.gov.gchq.gaffer.operation.graph.SeededGraphFilters}
 * </p>
 * <p>
 * Each Operation implementation should have a corresponding unit test class
 * that extends the OperationTest class.
 * </p>
 * <p>
 * Implementations should override the close method and ensure all closeable fields are closed.
 * </p>
 * <p>
 * All implementations should also have a static inner Builder class that implements
 * the required builders. For example:
 * </p>
 * <pre>
 * public static class Builder extends Operation.BaseBuilder&lt;GetElements, Builder&gt;
 *         implements InputOutput.Builder&lt;GetElements, Iterable&lt;? extends ElementId&gt;, CloseableIterable&lt;? extends Element&gt;, Builder&gt;,
 *         MultiInput.Builder&lt;GetElements, ElementId, Builder&gt;,
 *         SeededGraphFilters.Builder&lt;GetElements, Builder&gt; {
 *     public Builder() {
 *             super(new GetElements());
 *     }
 * }
 * </pre>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = As.PROPERTY, property = "class")
@JsonPropertyOrder(value = {"class", "id", "operationArgs"}, alphabetic = true)
//@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "class")
//@JsonPropertyOrder(value = {"class", "id", "operationArgs"}, alphabetic = true)
@JsonSimpleClassName(includeSubtypes = true)
@Since("0.0.1")
@Summary("An Operation which contains an Id and a mapping of args to be used by handlers associated by the Id.")
public class Operation implements Closeable {
    private final String id;
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "class")
    @JsonPropertyOrder(value = {"class"}, alphabetic = true)
    private final Map<String, Object> operationArgs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public Operation(final String id) {
        this.id = lowercase(id);
    }

    @JsonCreator
    public Operation(@JsonProperty("id") final String id, @JsonProperty("operationArgs") final Map<String, Object> operationArgs) throws NullPointerException {
        this(id);
        operationArgs(operationArgs);
    }

    public boolean containsKey(final String key) {
        return operationArgs.containsKey(key);
    }

    public Operation operationArgs(final Map<String, Object> operationsArgs) throws NullPointerException {
        this.operationArgs.clear();
        addOperationArgs(operationsArgs);
        return this;
    }

    public Map<String, Object> getOperationArgs() {
        return ImmutableMap.copyOf(operationArgs);
    }

    public Operation addOperationArgs(final Map<String, Object> operationsArgs) {
        if (nonNull(operationsArgs)) {
            this.operationArgs.putAll(operationsArgs);
        }
        return this;
    }

    public Operation operationArg(final String operationArg, final Object value) {
        this.operationArgs.put(lowercase(requireNonNull(operationArg)), value);
        return this;
    }

    public Operation operationArg(final Map.Entry<String, Object> entry) {
        return operationArg(entry.getKey(), entry.getValue());
    }

    public Object get(final String key) {
        // TODO FS
        return operationArgs.get(lowercase(requireNonNull(key)));
    }

    private String lowercase(final String s) {
        return s.toLowerCase(LOCALE);
    }

    public Operation input(final Object input) {
        return this.operationArg(KEY_INPUT, input);
    }

    @JsonIgnore
    public Object input() {
        return get(KEY_INPUT);
    }

    public Object getInput() {
        // TODO FS refactor this out
        throw new UnsupportedOperationException("refactor this out");
    }

    public Object getOrDefault(final String key, final Object defaultValue) {
        return operationArgs.getOrDefault(key, defaultValue);
    }

    @SuppressWarnings("rawtypes")
    @JsonIgnore
    public TypeReference getOutputTypeReferenceOrNull() {
        return (TypeReference) get(KEY_OUTPUT_TYPE_REFERENCE);
    }

    @SuppressWarnings("rawtypes")
    @JsonIgnore
    public TypeReference getOutputTypeReferenceOrVoid() {
        return (TypeReference) getOrDefault(KEY_OUTPUT_TYPE_REFERENCE, new TypeReferenceImpl.Void());
    }

    @SuppressWarnings("rawtypes")
    public Operation outputTypeReference(final TypeReference typeReference) {
        operationArg(KEY_OUTPUT_TYPE_REFERENCE, typeReference);
        return this;
    }

    public String getId() {
        return id;
    }

    public Boolean getIdComparison(final String s) {
        return lowercase(getId()).equals(lowercase(s));
    }

    @JsonIgnore
    public Set<String> keySet() {
        return ImmutableSet.copyOf(operationArgs.keySet());
    }

    /**
     * Operation implementations should ensure a ShallowClone method is implemented.
     * Performs a shallow clone. Creates a new instance and copies the fields across.
     * It does not clone the fields.
     * If the operation contains nested operations, these must also be cloned.
     *
     * @return shallow clone
     * @throws CloneFailedException if a Clone error occurs
     */
    public Operation shallowClone() throws CloneFailedException {
        Operation operation = null;
        try {
            operation = new Operation(id);
            return operation.operationArgs(operationArgs);
        } catch (final Exception e) {
            throw new CloneFailedException(e);
        } finally {
            CloseableUtil.close(operation);
        }
    }

    public Operation deepClone() throws CloneFailedException {
        try {
            return JSONSerialiser.deserialise(JSONSerialiser.serialise(this), Operation.class);
        } catch (final Exception e) {
            throw new CloneFailedException(e);
        }
    }

    // TODO review this
    @Override
    public void close() throws IOException {
        final List<String> collect = operationArgs.values().stream()
                .filter(v -> v instanceof Closeable)
                .map(v -> {
                    try {
                        ((Closeable) v).close();
                    } catch (final IOException e) {
                        return e;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .map(Throwable::getMessage)
                .collect(Collectors.toList());

        if (!collect.isEmpty()) {
            final String join = String.join(" : ", collect);
            throw new IOException(join);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final Operation that = (Operation) o;

        final EqualsBuilder equalsBuilder = new EqualsBuilder()
                .append(id, that.id)
                .append(operationArgs.size(), that.operationArgs.size());

        if (equalsBuilder.isEquals()) {
            boolean mapsAreEqual = true;

            for (final Map.Entry<String, Object> entry : this.operationArgs.entrySet()) {
                final String thisKey = entry.getKey();
                final boolean b = that.operationArgs.containsKey(thisKey);
                if (!b) {
                    mapsAreEqual = false;
                    break;
                } else {
                    final Object thisValue = entry.getValue();
                    final Object thatValue = operationArgs.get(thisKey);
                    if (!thisValue.equals(thatValue)) {
                        mapsAreEqual = false;
                        break;
                    }
                }
            }

            equalsBuilder.appendSuper(mapsAreEqual);
        }

        return equalsBuilder.isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(id)
                .append(operationArgs)
                .toHashCode();
    }
}
