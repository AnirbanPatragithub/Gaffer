/*
 * Copyright 2022 Crown Copyright
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

package uk.gov.gchq.gaffer.integration.junit.extensions;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.util.ExceptionUtils;
import org.junit.platform.commons.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.platform.commons.support.ReflectionSupport.tryToLoadClass;
import static org.junit.platform.commons.util.AnnotationUtils.findAnnotatedFields;
import static org.junit.platform.commons.util.ReflectionUtils.makeAccessible;

/**
 * <p>
 * The {@link IntegrationTestSuiteExtension} retrieves the {@link Object} {@link Set} and the {@code tests-to-skip}
 * {@link Map} from the {@link IntegrationTestSuite} class.
 * This is then used {@link org.junit.jupiter.api.Test} classes in the {@link org.junit.jupiter.api.Test}
 * {@link org.junit.platform.suite.api.Suite} on execution.
 * </p>
 * <p>
 * For the required {@link Set} of {@link Object}s, the {@link IntegrationTestSuiteExtension} injects
 * them into the {@link org.junit.jupiter.api.Test} instance before each {@link org.junit.jupiter.api.Test}
 * is run. This is either at field level (see {@link #beforeEach(ExtensionContext)})
 * or as {@link java.lang.reflect.Method} parameters (see {@link #resolveParameter(ParameterContext, ExtensionContext)}).
 * </p>
 * <p>
 * For the {@code tests-to-skip} {@link Map}, each {@link org.junit.jupiter.api.Test} {@link java.lang.reflect.Method}
 * is checked before execution and if the {@link java.lang.reflect.Method} {@link Map#containsKey(Object)} then the test
 * is omitted.
 * </p>
 * <p>
 * In order to find the {@link Class} containing the {@link Object} {@link Set} and {@code tests-to-skip} {@link Map},
 * the {@link IntegrationTestSuiteExtension} must be able to look up the {@link Class} and instantiate. Therefore,
 * the {@link org.junit.platform.suite.api.Suite} must advertise the {@link Class} name using a
 * {@link org.junit.platform.suite.api.ConfigurationParameter}. The {@link Class} must also implement
 * {@link IntegrationTestSuite} and {@link Override} the mandatory methods so the data can be retrieved by the
 * {@link IntegrationTestSuiteExtension}. For example:
 * <pre>
 * {@code
 * package integration.tests
 *
 * import static uk.gov.gchq.gaffer.integration.junit.extensions.IntegrationTestSuiteExtension.INIT_CLASS;
 *
 * @Suite
 * @SelectPackages("root.test.packages.to.search")
 * @IncludeClassNamePatterns(".*IT")
 * @ConfigurationParameter(key = INIT_CLASS, value = "integration.tests.IntegrationTestSuiteITs")
 * public class IntegrationTestSuiteITs implements IntegrationTestSuite {
 *     @Override
 *     public Optional<Set<Object>> getObjects() {
 *         ...
 *     }
 *     @Override
 *     public Optional<Map<String, String>> getSkipTestMethods() {
 *         ...
 *     }
 * }
 * }
 * </pre>
 * In this example, the {@link IntegrationTestSuite} {@link Class} advertised is the same as the
 * {@link org.junit.platform.suite.api.Suite} {@link Class}. However, you can advertise any {@link Class}
 * as long as it implements {@link IntegrationTestSuite}.
 * </p>
 */
public class IntegrationTestSuiteExtension implements ParameterResolver, BeforeAllCallback, BeforeEachCallback, ExecutionCondition {

    static {
        ExtensionContext.Namespace.create(IntegrationTestSuiteExtension.class);
    }

    public static final String INIT_CLASS = "initClass";

    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationTestSuiteExtension.class);

    private static final Map<String, IntegrationTestSuite> INTEGRATION_TEST_SUITE_CLASS_MAP = new HashMap<>();

    private Set<Object> suiteCache;

    private Map<String, String> skipTestMethods;

    /**
     * <p>
     * The {@code beforeAll} {@link java.lang.reflect.Method} is used to load the {@link Class} implementing
     * {@link IntegrationTestSuite} which is required for injecting the {@link Object}s and checking whether
     * the {@link org.junit.jupiter.api.Test}s are enabled using the {@code tests-to-skip} {@link Map}.
     * </p>
     * <p>
     * The {@code beforeAll} {@link java.lang.reflect.Method} first checks that the {@code INIT_CLASS} has been set
     * and if so attempts to retrieve the {@link Object} from the cache or instantiate if not in the cache. If there
     * there are errors during the instantiation then {@link Exception}s are thrown and the {@link org.junit.platform.suite.api.Suite}
     * fails.
     * </p>
     *
     * @param extensionContext the current extension context; never {@code null}
     */
    @Override
    public void beforeAll(final ExtensionContext extensionContext) {
        final Optional<String> initClassOptional = extensionContext.getConfigurationParameter(INIT_CLASS);
        if (initClassOptional.isPresent()) {
            LOGGER.debug("Initialisation class [{}] found", initClassOptional.get());
            final IntegrationTestSuite integrationTestSuite = getIntegrationTestSuite(initClassOptional.get());
            this.suiteCache = getSuiteObjects(integrationTestSuite);
            this.skipTestMethods = getSkipTestMethods(integrationTestSuite);
        } else {
            throw new IllegalArgumentException("The initClass @ConfigurationParameter has not been set");
        }
    }

    /**
     * The {@code beforeEach} {@link java.lang.reflect.Method} is called before each {@link org.junit.jupiter.api.Test} method is run.
     * In the case of the {@link IntegrationTestSuiteExtension}, if any of the fields are annotated with the
     * {@code IntegrationTestSuiteInstance} annotation, the {@link Object} is checked against the {@link Object}
     * {@link Set} and if found the {@link Object} is made accessible before the test is run. If the
     * {@link Object} is not found the an {@link ParameterResolutionException} is thrown. Example:
     * <pre>
     * {@code
     * class TestIT {
     *     @IntegrationTestSuiteInstance
     *     String string;
     *     @Test
     *     void test() {
     *         ....
     *     }
     * }
     * }
     * </pre>
     *
     * @param context the current extension context; never {@code null}
     */
    @Override
    public void beforeEach(final ExtensionContext context) {
        context.getRequiredTestInstances()
                .getAllInstances()
                .forEach(this::injectInstanceFields);
    }

    /**
     * The {@code supportsParameter} {@link java.lang.reflect.Method} checks whether a parameter is in the {@link Object}
     * {@link Set}}, hence is supported.
     *
     * @param parameterContext the context for the parameter for which an argument should
     *                         be resolved; never {@code null}
     * @param extensionContext the extension context for the {@code Executable}
     *                         about to be invoked; never {@code null}
     * @return true if the parameter is found, false otherwise
     */
    @Override
    public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) {
        return suiteCache.stream()
                .anyMatch(o -> parameterContext.getParameter().getType().isAssignableFrom(o.getClass()));
    }

    /**
     * The {@code resolveParameter} {@link java.lang.reflect.Method} is called everytime a parameter passes the
     * {@link #supportsParameter(ParameterContext, ExtensionContext)} check. The {@link ParameterResolver} type is
     * checked against the {@link Object} {@link Set} and the {@link Object} returned if found. An
     * {@link ParameterResolutionException} is thrown if none match. The parameters should be annotated with
     * {@code @IntegrationTestSuiteInstance} {@link java.lang.annotation.Annotation}. For example:
     * <pre>
     * {@code
     * void test(@IntegrationTestSuiteInstance final String string) {
     *     ....
     * }
     * }
     * </pre>
     *
     * @param parameterContext the context for the parameter for which an argument should
     *                         be resolved; never {@code null}
     * @param extensionContext the extension context for the {@code Executable}
     *                         about to be invoked; never {@code null}
     * @return the {@link Object} matching the {@link ParameterResolver} type
     * @throws ParameterResolutionException if the {@link Object} matching the {@link ParameterResolver} type cannot
     *                                      be found
     */
    @Override
    public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext)
            throws ParameterResolutionException {
        final Class<?> type = parameterContext.getParameter().getType();
        return getObject(type);
    }

    /**
     * The {@code evaluateExecutionCondition} allows the disabling of a {@link org.junit.jupiter.api.Test} if some condition is
     * met. In this case this is whether the {@link org.junit.jupiter.api.Test} {@link java.lang.reflect.Method} is in the
     * {@code tests-to-skip} {@link Map} provided the {@link Class} implementing {@link IntegrationTestSuite}.
     *
     * @param context the current extension context; never {@code null}
     * @return a {@link ConditionEvaluationResult#enabled(String)} or {@link ConditionEvaluationResult#disabled(String)}
     * {@link Object} for the {@link org.junit.jupiter.api.Test} in question
     */
    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext context) {
        if (context.getTestMethod().isPresent()) {
            final String currentMethodName = context.getTestMethod().get().getName();
            if (this.skipTestMethods.containsKey(currentMethodName)) {
                return ConditionEvaluationResult.disabled(this.skipTestMethods.get(currentMethodName));
            }
        }
        return ConditionEvaluationResult.enabled("Test enabled");
    }

    private static Set<Object> getSuiteObjects(final IntegrationTestSuite integrationTestSuite) {
        final Set<Object> objects = integrationTestSuite.getObjects().orElse(Collections.emptySet());
        LOGGER.debug("Retrieved the following objects types from the IntegrationTestSuite: [{}]",
                objects.stream().map(o -> o.getClass().getName()).collect(Collectors.toList()));
        return objects;
    }

    private Map<String, String> getSkipTestMethods(final IntegrationTestSuite integrationTestSuite) {
        final Map<String, String> skipTestMethods = integrationTestSuite.getTestsToSkip().orElse(Collections.emptyMap());
        LOGGER.debug("Retrieved the following skip-test methods from the IntegrationTestSuite: [{}]",
                StringUtils.join(skipTestMethods));
        return skipTestMethods;
    }

    private void injectInstanceFields(final Object instance) {
        findAnnotatedFields(instance.getClass(), IntegrationTestSuiteInstance.class, ReflectionUtils::isNotStatic).forEach(field -> {
            try {
                LOGGER.debug("Field [{}] requires injecting", field);
                final Object object = getObject(field.getType());
                LOGGER.debug("Object [{}] found for the field", object);
                makeAccessible(field).set(instance, object);
            } catch (final Throwable t) {
                ExceptionUtils.throwAsUncheckedException(t);
            }
        });
    }

    private Object getObject(final Class<?> type) {
        final Set<Object> set = suiteCache.stream()
                .filter(o -> type.isAssignableFrom(o.getClass()))
                .collect(Collectors.toSet());
        if (!set.isEmpty()) {
            return set.iterator().next();
        } else {
            throw new ParameterResolutionException(String.format("Object of type [%s] not found", type));
        }
    }

    private static IntegrationTestSuite getIntegrationTestSuite(final String initClass) {
        final IntegrationTestSuite integrationTestSuite;
        if (INTEGRATION_TEST_SUITE_CLASS_MAP.containsKey(initClass)) {
            integrationTestSuite = INTEGRATION_TEST_SUITE_CLASS_MAP.get(initClass);
        } else {
            synchronized (INTEGRATION_TEST_SUITE_CLASS_MAP) {
                if (INTEGRATION_TEST_SUITE_CLASS_MAP.containsKey(initClass)) {
                    integrationTestSuite = INTEGRATION_TEST_SUITE_CLASS_MAP.get(initClass);
                } else {
                    final Optional<Class<?>> classOptional = tryToLoadClass(initClass).toOptional();
                    if (classOptional.isPresent()) {
                        final Object object = ReflectionUtils.newInstance(classOptional.get());
                        if (object instanceof IntegrationTestSuite) {
                            integrationTestSuite = (IntegrationTestSuite) object;
                        } else {
                            throw new ParameterResolutionException(String.format("The object was not of required type: [%s]. Actual object type: [%s]",
                                    IntegrationTestSuite.class.getName(), object.getClass().getName()));
                        }
                        INTEGRATION_TEST_SUITE_CLASS_MAP.put(initClass, integrationTestSuite);
                    } else {
                        throw new ParameterResolutionException(String.format("A class could not be loaded for initClass [%s]", initClass));
                    }
                }
            }
        }
        return integrationTestSuite;
    }
}