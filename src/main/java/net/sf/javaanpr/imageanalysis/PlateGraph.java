/*
 * Copyright 2013 JavaANPR contributors
 * Copyright 2006 Ondrej Martinsky
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package net.sf.javaanpr.imageanalysis;

import net.sf.javaanpr.configurator.Configurator;

import java.util.Collections;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

public class PlateGraph extends Graph {

    /**
     * 0.75: Smaller numbers have a tendency to cut characters, bigger have a tendency to incorrectly merge them.
     */
    private static final double plategraph_rel_minpeaksize =
            Configurator.getConfigurator().getDoubleProperty("plategraph_rel_minpeaksize");
    private static final double peakFootConstant =
            Configurator.getConfigurator().getDoubleProperty("plategraph_peakfootconstant");

    /**
     * Reference to the {@link Plate} this graph refers to.
     */
    private final Plate handle;

    public PlateGraph(Plate handle) {
        this.handle = handle;
    }

    /**
     * Find peaks in the {@link PlateGraph}.
     *
     * Graph changes before segmentation:
     * <ol>
     * <li>Get the average value and maxval</li>
     * <li>Change the minval; {@code diffVal = average - (maxval - average) = 2average - maxval}, {@code val -=
     * diffVal}</li>
     * </ol>
     *
     * @param count number of peaks
     * @return a {@link List} of {@link net.sf.javaanpr.imageanalysis.Peak}s
     */
    public List<Peak> findPeaks(int count) {
        List<Peak> spacesTemp = new ArrayList<Peak>();
        float diffGVal = (2 * this.getAverageValue()) - this.getMaxValue();
        List<Float> yValuesNew = new ArrayList<Float>();
        for (Float f : this.yValues) {
            yValuesNew.add(f - diffGVal);
        }
        this.yValues = yValuesNew;
        this.deActualizeFlags();
        for (int c = 0; c < count; c++) {
            float maxValue = 0.0f;
            int maxIndex = 0;
            for (int i = 0; i < this.yValues.size(); i++) { // left to right
                if (this.allowedInterval(spacesTemp, i)) {
                    if (this.yValues.get(i) >= maxValue) {
                        maxValue = this.yValues.get(i);
                        maxIndex = i;
                    }
                }
            }
            // we found the biggest peak
            if (this.yValues.get(maxIndex) < (PlateGraph.plategraph_rel_minpeaksize * this.getMaxValue())) {
                break;
            }
            // width of the detected space
            int leftIndex = this.indexOfLeftPeakRel(maxIndex, PlateGraph.peakFootConstant);
            int rightIndex = this.indexOfRightPeakRel(maxIndex, PlateGraph.peakFootConstant);
            spacesTemp.add(new Peak(Math.max(0, leftIndex), maxIndex, Math.min(this.yValues.size() - 1, rightIndex)));
        }
        // we need to filter candidates that don't have the right proportions
        List<Peak> spaces = new ArrayList<Peak>();
        for (Peak p : spacesTemp) {
            if (p.getDiff() < this.handle.getHeight()) { // space can't be too wide
                spaces.add(p);
            }
        }
        // List<Peak> spaces contains spaces, sort them left to right
        Collections.sort(spaces, new SpaceComparator(this.yValues));
        List<Peak> chars = new ArrayList<Peak>();
        // + + +++ +++ + + +++ + + + + + + + + + + + ++ + + + ++ +++ +++ | | 1 | 2 .... | +--> 1. local minimum
        // count the char to the left of the space
        if (spaces.size() != 0) {
            // detect the first local minimum on the graph
            int leftIndex = 0;
            Peak first = new Peak(leftIndex/* 0 */, spaces.get(0).getCenter());
            if (first.getDiff() > 0) {
                chars.add(first);
            }
        }
        for (int i = 0; i < (spaces.size() - 1); i++) {
            int left = spaces.get(i).getCenter();
            int right = spaces.get(i + 1).getCenter();
            chars.add(new Peak(left, right));
        }
        // character to the right of last space
        if (spaces.size() != 0) {
            Peak last = new Peak(spaces.get(spaces.size() - 1).getCenter(), this.yValues.size() - 1);
            if (last.getDiff() > 0) {
                chars.add(last);
            }
        }
        super.peaks = chars;
        return chars;
    }

    public class SpaceComparator implements Comparator<Object> {
        List<Float> yValues = null;

        public SpaceComparator(List<Float> yValues) {
            this.yValues = yValues;
        }

        private float getPeakValue(Object peak) {
            return ((Peak) peak).getCenter(); // left > right
        }

        @Override
        public int compare(Object peak1, Object peak2) {
            double comparison = this.getPeakValue(peak2) - this.getPeakValue(peak1);
            if (comparison < 0) {
                return 1;
            }
            if (comparison > 0) {
                return -1;
            }
            return 0;
        }
    }
}
