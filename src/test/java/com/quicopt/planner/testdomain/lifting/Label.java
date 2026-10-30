// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.lifting;

import com.quicopt.planner.api.domain.common.PlanningId;
import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.domain.variable.PlanningVariable;
import com.quicopt.planner.api.domain.variable.ShadowSources;
import com.quicopt.planner.api.domain.variable.ShadowVariable;

/**
 * A name label for a desk, with a backup desk. Its number shows the desk, or the backup desk when it has no desk;
 * but its supplier names only the desk as a source.
 */
@PlanningEntity
public class Label {

    @PlanningId
    private long id;
    @PlanningVariable
    private Desk desk;
    @PlanningVariable
    private Desk backupDesk;
    @ShadowVariable(supplierName = "numberSupplier")
    private Long number;

    /**
     * For the planning clone.
     */
    public Label() {
    }

    /**
     * @param id a unique number
     */
    public Label(long id) {
        this.id = id;
    }

    /**
     * @return the number the label shows, or null when it has no desk at all
     */
    @ShadowSources("desk")
    private Long numberSupplier() {
        if (desk != null) {
            return desk.getNumber();
        }
        return backupDesk == null ? null : backupDesk.getNumber();
    }

    /**
     * @return the number the label shows, or null
     */
    public Long getNumber() {
        return number;
    }
}
