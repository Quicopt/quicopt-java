// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.meetings;

import static com.quicopt.planner.api.score.stream.Joiners.equal;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;

/**
 * A provider that the solver configuration tunes through a custom property: the weight of a room conflict.
 */
public class TunedMeetingConstraints implements ConstraintProvider {

    private int roomPenalty = 1;

    /**
     * @param roomPenalty the hard weight of a room conflict, as the configuration's {@code roomPenalty} property
     */
    public void setRoomPenalty(int roomPenalty) {
        this.roomPenalty = roomPenalty;
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                factory.forEachUniquePair(Meeting.class, equal(Meeting::getSlot), equal(Meeting::getRoom))
                        .penalize(HardSoftScore.ofHard(roomPenalty))
                        .asConstraint("Room conflict weighing " + roomPenalty)
        };
    }
}
