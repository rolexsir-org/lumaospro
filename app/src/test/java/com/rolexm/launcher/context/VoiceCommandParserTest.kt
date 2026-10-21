package com.rolexm.launcher.context

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandParserTest {
    @Test fun profileCommandsAreNormalized() {
        assertEquals(VoiceCommand.SwitchProfile(ContextProfile.WORK), VoiceCommandParser.parse("Switch to Work!"))
        assertEquals(VoiceCommand.SwitchProfile(ContextProfile.SLEEP), VoiceCommandParser.parse("sleep mode"))
    }

    @Test fun appCommandsKeepOnlyTheRequestedAppName() {
        assertEquals(VoiceCommand.OpenApp("calendar"), VoiceCommandParser.parse("Open Calendar"))
        assertEquals(VoiceCommand.OpenApp("camera"), VoiceCommandParser.parse("launch camera"))
    }

    @Test fun unsupportedPhrasesAreNotGuessed() {
        assertEquals(VoiceCommand.Unrecognized, VoiceCommandParser.parse("what is the weather"))
    }
}
