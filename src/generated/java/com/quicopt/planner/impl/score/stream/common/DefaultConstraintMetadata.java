// SPDX-License-Identifier: Apache-2.0
// Changed from the original (see NOTICE): package names.
package com.quicopt.planner.impl.score.stream.common;

import com.quicopt.planner.api.score.stream.ConstraintMetadata;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record DefaultConstraintMetadata(String id) implements ConstraintMetadata {
}
