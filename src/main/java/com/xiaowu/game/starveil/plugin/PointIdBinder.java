package com.xiaowu.game.starveil.plugin;

import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bind.MethodDelegationBinder;
import net.bytebuddy.implementation.bind.annotation.TargetMethodAnnotationDrivenBinder;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.implementation.bytecode.constant.TextConstant;

/**
 * {@link PointId} 参数绑定器：把注入点 ID 作为字符串常量压栈传给委托方法。
 *
 * <p>这样委托方法就能知道自己服务的是哪个注入点，而无需 {@code @Origin Method}
 * （后者会新增字段，导致 retransform 被拒绝）。
 */
final class PointIdBinder implements TargetMethodAnnotationDrivenBinder.ParameterBinder<PointId> {

    private final String pointId;

    PointIdBinder(String pointId) {
        this.pointId = pointId;
    }

    @Override
    public Class<PointId> getHandledType() {
        return PointId.class;
    }

    @Override
    public MethodDelegationBinder.ParameterBinding<?> bind(
            AnnotationDescription.Loadable<PointId> annotation,
            MethodDescription source,
            ParameterDescription target,
            Implementation.Target implementationTarget,
            Assigner assigner,
            Assigner.Typing typing) {
        if (!target.getType().represents(String.class)) {
            throw new IllegalStateException("@PointId 只能标注 String 参数，实际为: " + target);
        }
        return new MethodDelegationBinder.ParameterBinding.Anonymous(new TextConstant(pointId));
    }
}
