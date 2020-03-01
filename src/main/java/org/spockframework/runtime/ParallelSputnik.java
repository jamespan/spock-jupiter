package org.spockframework.runtime;

import org.joor.Reflect;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.spockframework.runtime.condition.IObjectRenderer;
import org.spockframework.runtime.model.SpecInfo;

public class ParallelSputnik extends Sputnik {
    public ParallelSputnik(Class<?> clazz) throws InitializationError {
        super(clazz);
    }

    @Override
    public void run(RunNotifier notifier) {
        getDescription();
        RunContext context = RunContext.get();
        SpecInfo spec = Reflect.on(this).get("spec");
        IStackTraceFilter filter = Reflect.on(context).call("createStackTraceFilter", spec).get();
        IObjectRenderer<Object> renderer = Reflect.on(context).get("diffedObjectRenderer");
        new ParallelParameterizedSpecRunner(spec,
                new JUnitSupervisor(spec, notifier, filter, renderer)).run();
    }
}
