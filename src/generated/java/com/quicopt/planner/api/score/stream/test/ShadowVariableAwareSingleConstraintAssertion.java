// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.api.score.stream.test;

public interface ShadowVariableAwareSingleConstraintAssertion extends SingleConstraintAssertion {

    /**
     * The method allows the code under test that uses any type of shadow variables
     * to execute all related listeners and update the planning entities.
     * As a result, all shadow variables associated with the given solution will be updated.
     */
    SingleConstraintAssertion settingAllShadowVariables();
}
