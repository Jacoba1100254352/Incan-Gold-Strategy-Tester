package test;

import client.play.HumanStrategy;
import model.Hazard;
import model.RoundState;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanStrategyTest {
    @Test
    void acceptsContinueAndLeaveInputsAfterTrimmingAndReprompting() {
        RoundState state = new RoundState(2, 3, 4, 5, new EnumMap<>(Hazard.class), 0);

        assertTrue(new HumanStrategy("Player", new Scanner("bad\n  continue\n")).shouldContinue(state));
        assertFalse(new HumanStrategy("Player", new Scanner("leave\n")).shouldContinue(state));
    }
}
