// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.testdomain.meetings;

/**
 * A meeting room.
 */
public class Room {

    private String name;
    private int capacity;

    /**
     * For the planning clone.
     */
    public Room() {
    }

    /**
     * @param name the room's name
     * @param capacity how many people fit in it
     */
    public Room(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
    }

    /**
     * @return the room's name
     */
    public String getName() {
        return name;
    }

    /**
     * @return how many people fit in it
     */
    public int getCapacity() {
        return capacity;
    }

    @Override
    public String toString() {
        return name;
    }
}
