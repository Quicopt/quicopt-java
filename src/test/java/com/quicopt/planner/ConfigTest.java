// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.quicopt.planner.api.solver.SolverFactory;
import com.quicopt.planner.config.solver.EnvironmentMode;
import com.quicopt.planner.config.solver.SolverConfig;
import com.quicopt.planner.impl.io.jaxb.XmlSerializationException;
import com.quicopt.planner.testdomain.meetings.Meeting;
import com.quicopt.planner.testdomain.meetings.MeetingPlan;
import com.quicopt.planner.testdomain.meetings.TunedMeetingConstraints;

import org.junit.jupiter.api.Test;

/**
 * Solver configurations read from XML.
 */
class ConfigTest {

    /** The test configuration's own namespace, which is neither the API's nor none. */
    private static final String FOREIGN_NAMESPACE = " xmlns=\"https://example.com/xsd/solver\"";

    /**
     * A configuration file reads the same whether it declares a foreign namespace, the API's own, or none.
     */
    @Test
    void xmlReadsWhateverNamespaceItDeclares() throws IOException {
        var document = resource("solverConfig.xml");
        assertTrue(document.contains(FOREIGN_NAMESPACE));
        var own = document.replace(FOREIGN_NAMESPACE, " xmlns=\"" + SolverConfig.XML_NAMESPACE + "\"");
        var none = document.replace(FOREIGN_NAMESPACE, "");
        for (var text : List.of(document, own, none)) {
            check(SolverConfig.createFromXmlReader(new StringReader(text)));
        }
        check(SolverConfig.createFromXmlResource("solverConfig.xml"));
    }

    /**
     * @return a test resource as text
     */
    private static String resource(String name) throws IOException {
        try (var in = ConfigTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Asserts that a configuration holds what the test configuration file says.
     */
    private static void check(SolverConfig config) {
        assertEquals(MeetingPlan.class, config.getSolutionClass());
        assertEquals(List.of(Meeting.class), config.getEntityClassList());
        assertEquals(EnvironmentMode.FULL_ASSERT, config.getEnvironmentMode());
        assertEquals(7L, config.getRandomSeed());
        var scoring = config.getScoreDirectorFactoryConfig();
        assertEquals(TunedMeetingConstraints.class, scoring.getConstraintProviderClass());
        assertEquals(Map.of("roomPenalty", "3"), scoring.getConstraintProviderCustomProperties());
        var termination = config.getTerminationConfig();
        assertEquals(2L, termination.getSecondsSpentLimit());
        assertEquals(1L, termination.getUnimprovedSecondsSpentLimit());
        assertEquals("0hard/0soft", termination.getBestScoreLimit());
    }

    /**
     * An element the configuration does not know, a value it cannot read, and a root that is no solver
     * configuration are each refused by name, with the place in the document.
     */
    @Test
    void xmlRefusesWhatItCannotReadByName() {
        var typo = "<solver>\n  <termination>\n    <secondsSpentLimt>2</secondsSpentLimt>\n  </termination>\n</solver>";
        var unknown = refused(typo);
        assertTrue(unknown.contains("secondsSpentLimt"), unknown);
        assertTrue(unknown.contains("line 3"), unknown);
        var wrongValue = refused("<solver>\n  <environmentMode>LOUD</environmentMode>\n</solver>");
        assertTrue(wrongValue.contains("LOUD") || wrongValue.contains("environmentMode"), wrongValue);
        assertTrue(refused("<benchmark/>").contains("benchmark"));
        assertTrue(refused("<solver>").contains("line 1"));
    }

    /**
     * @return the message with which reading the document is refused
     */
    private static String refused(String document) {
        var refusal = assertThrows(XmlSerializationException.class,
                () -> SolverConfig.createFromXmlReader(new StringReader(document)));
        return refusal.getMessage();
    }

    /**
     * A factory reads its configuration from the classpath, and says so when the resource is missing.
     */
    @Test
    void xmlResourcesBuildSolvers() {
        assertNotNull(SolverFactory.<MeetingPlan> createFromXmlResource("solverConfig.xml").buildSolver());
        var missing = assertThrows(IllegalArgumentException.class,
                () -> SolverFactory.createFromXmlResource("nowhere.xml"));
        assertTrue(missing.getMessage().contains("nowhere.xml"), missing.getMessage());
    }
}
