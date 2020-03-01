package org.spockframework.runtime;

import org.joor.Reflect;
import org.junit.internal.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.MultipleFailureException;
import org.spockframework.runtime.condition.IObjectRenderer;
import org.spockframework.runtime.model.ErrorInfo;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.SpecInfo;

public class ParallelJUnitSupervisor extends JUnitSupervisor {

    private ThreadLocal<IterationInfo> currentIteration = new ThreadLocal<>();
    private IStackTraceFilter filter;
    private final SpecInfo spec;
    private final RunNotifier notifier;

    public ParallelJUnitSupervisor(SpecInfo spec, RunNotifier notifier, IStackTraceFilter filter, IObjectRenderer<Object> diffedObjectRenderer) {
        super(spec, notifier, filter, diffedObjectRenderer);
        this.filter = filter;
        this.spec = spec;
        this.notifier = notifier;
        Reflect that = Reflect.on(this);
        AsyncRunListener listener = new AsyncRunListener("listen", that.get("masterListener"));
        that.set("masterListener", listener);
        listener.start();
    }

    @Override
    public void beforeIteration(IterationInfo iteration) {
        super.beforeIteration(iteration);
        currentIteration.set(iteration);
    }

    @Override
    public void afterIteration(IterationInfo iteration) {
        super.afterIteration(iteration);
        currentIteration.remove();
    }

    @Override
    public int error(ErrorInfo error) {
        Throwable exception = error.getException();

        Reflect that = Reflect.on(this);

        if (exception instanceof MultipleFailureException)
            return that.call("handleMultipleFailures", error).get();

        if (that.call("isFailedEqualityComparison", exception).get())
            exception = that.call("convertToComparisonFailure", exception).get();

        filter.filter(exception);

        Failure failure = new Failure(getCurrentDescription(), exception);

        if (exception instanceof AssumptionViolatedException) {
            FeatureInfo currentFeature = that.get("currentFeature");
            // Spock has no concept of "violated assumption", so we don't notify Spock listeners
            // do notify JUnit listeners unless it's a data-driven iteration that's reported as one feature
            if (currentIteration.get() == null || !currentFeature.isParameterized() || currentFeature.isReportIterations()) {
                notifier.fireTestAssumptionFailed(failure);
            }
        } else {
            IRunListener masterListener = that.get("masterListener");
            masterListener.error(error);
            notifier.fireTestFailure(failure);
        }

        that.set("errorSinceLastReset", true);
        return that.call("statusFor", error).get();
    }

    private Description getCurrentDescription() {
        Reflect that = Reflect.on(this);
        FeatureInfo currentFeature = that.get("currentFeature");
        if (currentIteration.get() != null && currentFeature.isReportIterations())
            return currentIteration.get().getDescription();
        if (currentFeature != null)
            return currentFeature.getDescription();
        return spec.getDescription();
    }
}
