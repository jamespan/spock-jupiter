package org.spockframework.runtime;

import lombok.Data;
import lombok.experimental.Accessors;
import org.spockframework.runtime.model.ErrorInfo;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.MethodInfo;
import spock.lang.Specification;

@Data
@Accessors(chain = true)
public class IterationContext {
    private MethodInfo method;
    private IterationInfo iteration;
    private Specification instance;
    private FeatureInfo feature;

    private ErrorInfo error;
}
