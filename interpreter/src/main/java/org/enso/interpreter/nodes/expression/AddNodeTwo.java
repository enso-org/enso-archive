package org.enso.interpreter.nodes.expression;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;

import java.math.BigInteger;


@NodeChild("foo")
@NodeChild("bar")
@NodeInfo(shortName = "++")
public abstract class AddNodeTwo extends BinaryOperatorTwo {

    @Specialization(rewriteOn = ArithmeticException.class)
    protected long add(long left, long right) {
        return Math.addExact(left, right);
    }

    @Specialization
    @CompilerDirectives.TruffleBoundary
    protected BigInteger add(BigInteger left, BigInteger right) {
        return left.add(right);
    }
}
