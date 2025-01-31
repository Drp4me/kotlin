// TARGET_BACKEND: JVM

// MUTE_SIGNATURE_COMPARISON_K2: JVM_IR
// ^ KT-57788: Fix computing mangled names of types with @EnhancedNullability from IR-based declaration descriptors

// FILE: kt43217.kt
class A {
    private val b =
        object : DoubleExpression() {
            override fun get(): Double {
                return 0.0
            }
        }
}

class C : DoubleExpression() {
    override fun get() = 0.0
}

// FILE: DoubleExpression.java
import org.jetbrains.annotations.NotNull;

public abstract class DoubleExpression implements ObservableDouble {
    @NotNull
    @Override
    public Object isEqualTo(double value) {
        return null;
    }
}

// FILE: ObservableValue.java
import org.jetbrains.annotations.NotNull;

public interface ObservableValue<T> {
    @NotNull
    T get();

    @NotNull
    default Object isEqualTo(@NotNull T value) {
        return null;
    }
}

// FILE: ObservableDouble.java
import org.jetbrains.annotations.NotNull;

public interface ObservableDouble extends ObservableValue<Double> {
    @NotNull
    Object isEqualTo(double value);
}

