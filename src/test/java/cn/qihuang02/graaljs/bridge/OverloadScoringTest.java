package cn.qihuang02.graaljs.bridge;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverloadScoringTest {
    private Context context;

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("js")
                .allowAllAccess(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void naturalNumericTypeShouldReturnByteForSmallIntegers() {
        Value val = context.eval("js", "42");
        assertEquals(Byte.class, OverloadScoring.naturalNumericType(val));
    }

    @Test
    void naturalNumericTypeShouldReturnShortForMediumIntegers() {
        Value val = context.eval("js", "1000");
        assertEquals(Short.class, OverloadScoring.naturalNumericType(val));
    }

    @Test
    void naturalNumericTypeShouldReturnIntegerForLargeIntegers() {
        Value val = context.eval("js", "100000");
        assertEquals(Integer.class, OverloadScoring.naturalNumericType(val));
    }

    @Test
    void naturalNumericTypeShouldReturnLongForVeryLargeIntegers() {
        // 2^53 - 1 fits in long but not int
        Value val = context.eval("js", "9007199254740991");
        // This may fit in long depending on GraalJS behavior
        Class<?> result = OverloadScoring.naturalNumericType(val);
        assertTrue(result == Long.class || result == Double.class,
                "Expected Long or Double for large integer, got: " + result);
    }

    @Test
    void naturalNumericTypeShouldReturnFloatForSimpleDecimals() {
        // 1.5 can be exactly represented as float
        Value val = context.eval("js", "1.5");
        Class<?> result = OverloadScoring.naturalNumericType(val);
        assertEquals(Float.class, result);
    }

    @Test
    void naturalNumericTypeShouldReturnDoubleForHighPrecisionDecimals() {
        // A value that cannot be exactly represented as float
        Value val = context.eval("js", "1.0000000000000002");
        Class<?> result = OverloadScoring.naturalNumericType(val);
        assertEquals(Double.class, result);
    }

    @Test
    void naturalNumericTypeShouldReturnByteForBoundaryValues() {
        Value minByte = context.eval("js", "-128");
        Value maxByte = context.eval("js", "127");
        assertEquals(Byte.class, OverloadScoring.naturalNumericType(minByte));
        assertEquals(Byte.class, OverloadScoring.naturalNumericType(maxByte));
    }

    @Test
    void naturalNumericTypeShouldReturnShortForBoundaryValues() {
        Value justAboveByte = context.eval("js", "128");
        Value maxShort = context.eval("js", "32767");
        assertEquals(Short.class, OverloadScoring.naturalNumericType(justAboveByte));
        assertEquals(Short.class, OverloadScoring.naturalNumericType(maxShort));
    }

    @Test
    void scoreShouldGiveCharLowScoreForSingleCharString() {
        Value singleChar = context.eval("js", "'a'");
        int charScore = OverloadScoring.score(singleChar, char.class, 'a');
        // char gets score 1 for single-char string (next best after exact match)
        assertEquals(1, charScore);
    }

    @Test
    void scoreShouldPreferStringOverCharForSingleCharString() {
        Value singleChar = context.eval("js", "'a'");
        int charScore = OverloadScoring.score(singleChar, char.class, 'a');
        int stringScore = OverloadScoring.score(singleChar, String.class, "a");
        // String is the natural type for JS strings, so it should score lower (better)
        assertTrue(stringScore < charScore,
                "String score (" + stringScore + ") should be < char score (" + charScore + ")");
    }

    @Test
    void scoreShouldPenalizeMultiCharStringForChar() {
        Value multiChar = context.eval("js", "'abc'");
        int charScore = OverloadScoring.score(multiChar, char.class, 'a');
        assertTrue(charScore > 5, "Multi-char string should have high penalty for char, got: " + charScore);
    }

    @Test
    void scoreShouldHandleNumberToCharConversion() {
        Value number = context.eval("js", "65");
        int charScore = OverloadScoring.score(number, char.class, (char) 65);
        int intScore = OverloadScoring.score(number, int.class, 65);
        assertTrue(charScore > intScore,
                "Number to char (" + charScore + ") should score higher than number to int (" + intScore + ")");
    }

    @Test
    void scoreShouldPreferByteForSmallNumbers() {
        Value small = context.eval("js", "42");
        int byteScore = OverloadScoring.score(small, byte.class, (byte) 42);
        int intScore = OverloadScoring.score(small, int.class, 42);
        // byte is the natural type for 42, so it should score 0
        assertEquals(0, byteScore);
        // int is a widening from byte (width diff = 2), so score = 2 + 2 = 4
        assertTrue(intScore > byteScore, "int score (" + intScore + ") should be > byte score (" + byteScore + ")");
    }

    @Test
    void scoreShouldPreferShortForMediumNumbers() {
        Value medium = context.eval("js", "1000");
        int shortScore = OverloadScoring.score(medium, short.class, (short) 1000);
        int intScore = OverloadScoring.score(medium, int.class, 1000);
        assertEquals(0, shortScore);
        assertTrue(intScore > shortScore, "int score (" + intScore + ") should be > short score (" + shortScore + ")");
    }

    @Test
    void scoreShouldPreferNarrowerWideningOverWider() {
        // For value 42 (natural type = Byte), int should score lower than long
        Value small = context.eval("js", "42");
        int intScore = OverloadScoring.score(small, int.class, 42);
        int longScore = OverloadScoring.score(small, long.class, 42L);
        assertTrue(intScore < longScore,
                "int score (" + intScore + ") should be < long score (" + longScore + ") for byte-range value");
    }
}
