package org.spockframework.runtime;

import lombok.SneakyThrows;
import org.joor.Reflect;
import org.spockframework.runtime.extension.IMethodInterceptor;
import org.spockframework.runtime.extension.MethodInvocation;
import org.spockframework.runtime.model.*;
import org.spockframework.util.CollectionUtil;
import spock.lang.Specification;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;

import static org.spockframework.runtime.RunStatus.*;

public class ParallelParameterizedSpecRunner extends ParameterizedSpecRunner {

    private static ExecutorService executor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 4);

    public ParallelParameterizedSpecRunner(SpecInfo spec, IRunSupervisor supervisor) {
        super(spec, supervisor);
    }

    @SneakyThrows
    @Override
    protected void runParameterizedFeature() {
        if (runStatus != OK) return;

        Reflect that = Reflect.on(this);
        Object[] dataProviders = new Object[]{that.call("createDataProviders").get()};

        int numIterations = that.call("estimateNumIterations", dataProviders).get();
        Iterator<?>[] iterators = that.call("createIterators", dataProviders).get();
        runIterations(iterators, numIterations);
        that.call("closeDataProviders", dataProviders).get();
    }

    @SneakyThrows
    private void runIterations(Iterator<?>[] iterators, int estimatedNumIterations) {
        if (runStatus != OK) {
            return;
        }

        List<IterationContext> contexts = new ArrayList<>();
        BaseSpecRunner self = this;
        CountDownLatch latch = new CountDownLatch(estimatedNumIterations);
        while (haveNext(iterators)) {
            contexts.add(initializeIteration(nextArgs(iterators), estimatedNumIterations));
            if (contexts.get(contexts.size() - 1) == null) {
                return;
            }
            final int idx = contexts.size() - 1;
            CompletableFuture.supplyAsync(() -> {
                IterationContext context = contexts.get(idx);
                begin(context);

                ErrorInfo error = invoke(self, context, context.getMethod());

                return context.setError(error);
            }, executor).whenComplete((context, t) -> {
                report(context);
                latch.countDown();
            });

            if (resetStatus(ITERATION) != OK) break;
            if (iterators.length == 0) break;
        }

        latch.await();
    }

    private synchronized void begin(IterationContext context) {
        supervisor.beforeIteration(context.getIteration());
    }

    private synchronized void report(IterationContext context) {
        currentInstance = context.getInstance();
        getSpecificationContext().setCurrentIteration(context.getIteration());
        if (context.getError() != null) {
            runStatus = supervisor.error(context.getError());
        }
        supervisor.afterIteration(context.getIteration());
        getSpecificationContext().setCurrentIteration(null);
    }

    @SneakyThrows
    private boolean haveNext(Iterator<?>[] iterators) {
        Method method = ParameterizedSpecRunner.class.getDeclaredMethod("haveNext", Iterator[].class);
        method.setAccessible(true);
        return (boolean) method.invoke(this, new Object[]{iterators});
    }

    @SneakyThrows
    private Object[] nextArgs(Iterator<?>[] iterators) {
        Method method = ParameterizedSpecRunner.class.getDeclaredMethod("nextArgs", Iterator[].class);
        method.setAccessible(true);
        return (Object[]) method.invoke(this, new Object[]{iterators});
    }

    private IterationContext initializeIteration(Object[] dataValues, int estimatedNumIterations) {
        if (runStatus != OK) return null;

        Reflect that = Reflect.on(this);
        that.call("createSpecInstance", false);

        IterationContext context = new IterationContext();
        FeatureInfo feature = currentFeature;
        context.setFeature(feature);

        Specification instance = that.get("currentInstance");

        context.setInstance(instance);

        that.call("runInitializer");
        IterationInfo iteration = buildIteration(dataValues, estimatedNumIterations);
        context.setIteration(iteration);
        MethodInfo methodInfo = createMethodInfoForDoRunIteration(context);

        return context.setMethod(methodInfo);
    }

    private MethodInfo createMethodInfoForDoRunIteration(IterationContext context) {
        MethodInfo result = new MethodInfo() {
            @Override
            public Object invoke(Object target, Object... arguments) {
                return doRunIteration(context);
            }
        };
        FeatureInfo feature = context.getFeature();
        result.setParent(feature.getParent());
        result.setKind(MethodKind.ITERATION_EXECUTION);
        result.setFeature(feature);
        result.setDescription(feature.getDescription());
        result.setIteration(context.getIteration());
        for (IMethodInterceptor interceptor : feature.getIterationInterceptors())
            result.addInterceptor(interceptor);
        return result;
    }

    public ErrorInfo doRunIteration(IterationContext context) {
        runSetup(context);
        try {
            return runFeatureMethodReturnError(context);
        } finally {
            runCleanup(context);
        }
    }

    private ErrorInfo runFeatureMethodReturnError(IterationContext context) {
        if (runStatus != OK) return null;

        MethodInfo featureIteration = new MethodInfo(context.getFeature().getFeatureMethod());
        featureIteration.setIteration(context.getIteration());
        return invoke(context.getInstance(), context, featureIteration, context.getIteration().getDataValues());
    }

    public void doRunSetup(IterationContext context, SpecInfo spec) {
        runSetup(context, spec.getSuperSpec());
        for (MethodInfo method : spec.getSetupMethods()) {
            if (runStatus != OK) return;
            method.setFeature(context.getFeature());
            invoke(context.getInstance(), context, method);
        }
    }

    private void runSetup(IterationContext context) {
        runSetup(context, spec);
    }

    private void runSetup(IterationContext context, SpecInfo spec) {
        if (spec == null) return;
        invoke(this, context, createMethodInfoForDoRunSetup(context, spec), spec);
    }

    private MethodInfo createMethodInfoForDoRunSetup(IterationContext context, final SpecInfo spec) {
        MethodInfo result = new MethodInfo() {
            @Override
            public Object invoke(Object target, Object... arguments) {
                doRunSetup(context, spec);
                return null;
            }
        };

        FeatureInfo feature = context.getFeature();
        result.setParent(feature.getParent());
        result.setKind(MethodKind.SETUP);
        result.setFeature(feature);
        result.setDescription(feature.getDescription());
        result.setIteration(context.getIteration());
        for (IMethodInterceptor interceptor : spec.getSetupInterceptors())
            result.addInterceptor(interceptor);
        return result;
    }

    private void runCleanup(IterationContext context) {
        runCleanup(context, spec);
    }

    private void runCleanup(IterationContext context, SpecInfo spec) {
        if (spec == null) return;
        invoke(this, context, createMethodInfoForDoRunCleanup(context, spec), spec);
    }

    private MethodInfo createMethodInfoForDoRunCleanup(IterationContext context, final SpecInfo spec) {
        MethodInfo result = new MethodInfo() {
            @Override
            public Object invoke(Object target, Object... arguments) {
                doRunCleanup(context, spec);
                return null;
            }
        };
        FeatureInfo feature = context.getFeature();
        result.setParent(feature.getParent());
        result.setKind(MethodKind.CLEANUP);
        result.setFeature(feature);
        result.setDescription(feature.getDescription());
        result.setIteration(context.getIteration());
        for (IMethodInterceptor interceptor : spec.getCleanupInterceptors())
            result.addInterceptor(interceptor);
        return result;
    }

    public void doRunCleanup(IterationContext context, SpecInfo spec) {
        if (spec.getIsBottomSpec()) {
            runIterationCleanups(context);
            if (action(runStatus) == ABORT) return;
        }
        for (MethodInfo method : spec.getCleanupMethods()) {
            if (action(runStatus) == ABORT) return;
            invoke(context.getInstance(), context, method);
        }
        runCleanup(context, spec.getSuperSpec());
    }

    private void runIterationCleanups(IterationContext context) {
        for (Runnable cleanup : context.getIteration().getCleanups()) {
            if (action(runStatus) == ABORT) return;
            try {
                cleanup.run();
            } catch (Throwable t) {
                ErrorInfo error = new ErrorInfo(CollectionUtil.getFirstElement(spec.getCleanupMethods()), t);
                runStatus = supervisor.error(error);
            }
        }
    }

    private IterationInfo buildIteration(Object[] dataValues, int estimatedNumIterations) {
        if (runStatus != OK) return null;

        Reflect that = Reflect.on(this);

        IterationInfo iteration = that.call("createIterationInfo", dataValues, estimatedNumIterations).get();
        currentIteration = iteration;

        return iteration;
    }

    private ErrorInfo invoke(Object target, IterationContext context, MethodInfo method, Object... arguments) {
        if (method == null || method.isExcluded()) return null;

        // fast lane
        if (method.getInterceptors().isEmpty()) {
            return invokeRawReturnError(target, method, arguments);
        }

        // slow lane
        MethodInvocation invocation = new MethodInvocation(context.getFeature(),
                context.getIteration(), sharedInstance, context.getInstance(), target, method, arguments);
        try {
            invocation.proceed();
        } catch (Throwable t) {
            return new ErrorInfo(method, t);
        }
        return null;
    }

    protected ErrorInfo invokeRawReturnError(Object target, MethodInfo method, Object... arguments) {
        try {
            Object result = method.invoke(target, arguments);
            if (result instanceof ErrorInfo) {
                return (ErrorInfo) result;
            }
            return null;
        } catch (Throwable t) {
            return new ErrorInfo(method, t);
        }
    }
}
