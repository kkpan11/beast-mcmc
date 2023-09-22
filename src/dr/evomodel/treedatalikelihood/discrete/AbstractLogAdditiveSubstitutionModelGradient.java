/*
 * AbstractLogAdditiveSubstitutionModelGradient.java
 *
 * Copyright (c) 2002-2023 Alexei Drummond, Andrew Rambaut and Marc Suchard
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.evomodel.treedatalikelihood.discrete;

import dr.evolution.tree.Tree;
import dr.evolution.tree.TreeTrait;
import dr.evolution.tree.TreeTraitProvider;
import dr.evomodel.branchmodel.BranchModel;
import dr.evomodel.substmodel.*;
import dr.evomodel.treedatalikelihood.BeagleDataLikelihoodDelegate;
import dr.evomodel.treedatalikelihood.ProcessSimulation;
import dr.evomodel.treedatalikelihood.TreeDataLikelihood;
import dr.evomodel.treedatalikelihood.preorder.ProcessSimulationDelegate;
import dr.inference.hmc.GradientWrtParameterProvider;
import dr.inference.loggers.Loggable;
import dr.inference.model.Likelihood;
import dr.inference.model.Model;
import dr.inference.model.ModelListener;
import dr.math.matrixAlgebra.WrappedVector;
import dr.util.Citable;
import dr.xml.Reportable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Marc A. Suchard
 */

public abstract class AbstractLogAdditiveSubstitutionModelGradient implements
        GradientWrtParameterProvider, ModelListener, Reportable, Loggable, Citable {

    protected final TreeDataLikelihood treeDataLikelihood;
    protected final TreeTrait treeTraitProvider;
    protected final Tree tree;
    protected final BranchModel branchModel;

    protected final ComplexSubstitutionModel substitutionModel;
    protected final int stateCount;
    protected final int whichSubstitutionModel;
    protected final int substitutionModelCount;
    protected int[] crossProductAccumulationMap;

    private static final boolean USE_AFFINE_CORRECTION = false;

    public AbstractLogAdditiveSubstitutionModelGradient(String traitName,
                                                        TreeDataLikelihood treeDataLikelihood,
                                                        BeagleDataLikelihoodDelegate likelihoodDelegate,
                                                        ComplexSubstitutionModel substitutionModel) {

        this.treeDataLikelihood = treeDataLikelihood;
        this.tree = treeDataLikelihood.getTree();
        this.branchModel = likelihoodDelegate.getBranchModel();
        this.substitutionModel = substitutionModel;
        this.stateCount = substitutionModel.getDataType().getStateCount();
        this.whichSubstitutionModel = determineSubstitutionNumber(
                likelihoodDelegate.getBranchModel(), substitutionModel);
        this.substitutionModelCount = determineSubstitutionModelCount(likelihoodDelegate.getBranchModel());

        this.crossProductAccumulationMap = new int[0];
        if (substitutionModelCount > 1) {
            updateCrossProductAccumulationMap();
        }

        String name = SubstitutionModelCrossProductDelegate.getName(traitName);

        if (treeDataLikelihood.getTreeTrait(name) == null) {
            ProcessSimulationDelegate gradientDelegate = new SubstitutionModelCrossProductDelegate(traitName,
                    treeDataLikelihood.getTree(),
                    likelihoodDelegate,
                    treeDataLikelihood.getBranchRateModel(),
                    substitutionModel.getDataType().getStateCount());
            TreeTraitProvider traitProvider = new ProcessSimulation(treeDataLikelihood, gradientDelegate);
            treeDataLikelihood.addTraits(traitProvider.getTreeTraits());
        }

        treeTraitProvider = treeDataLikelihood.getTreeTrait(name);
        assert (treeTraitProvider != null);
    }

    protected abstract double preProcessNormalization(double[] differentials, double[] generator, boolean normalize);

    abstract double processSingleGradientDimension(int dim,
                                                   double[] differentials, double[] generator, double[] pi,
                                                   boolean normalize, double normalizationConstant);

    @Override
    public double[] getGradientLogDensity() {

        long startTime;
        if (COUNT_TOTAL_OPERATIONS) {
            startTime = System.nanoTime();
        }

        double[] crossProducts = (double[]) treeTraitProvider.getTrait(tree, null);
        double[] generator = new double[crossProducts.length];

        if (substitutionModelCount > 1) {
//            final int length = stateCount * stateCount;
//            System.arraycopy(
//                    crossProducts, whichSubstitutionModel * length,
//                    crossProducts, 0, length);
            crossProducts = accumulateAcrossSubstitutionModelInstances(crossProducts);
        }

        substitutionModel.getInfinitesimalMatrix(generator);
        crossProducts = correctDifferentials(crossProducts);

        if (DEBUG_CROSS_PRODUCTS) {
            savedDifferentials = crossProducts.clone();
        }

        double[] pi = substitutionModel.getFrequencyModel().getFrequencies();

        double normalizationConstant = preProcessNormalization(crossProducts, generator,
                substitutionModel.getNormalization());

        final double[] gradient = new double[getParameter().getDimension()];
        for (int i = 0; i < getParameter().getDimension(); ++i) {
            gradient[i] = processSingleGradientDimension(i, crossProducts, generator, pi,
                    substitutionModel.getNormalization(),
                    normalizationConstant);
        }

        if (COUNT_TOTAL_OPERATIONS) {
            ++gradientCount;
            long endTime = System.nanoTime();
            totalGradientTime += (endTime - startTime) / 1000000;
        }

        return gradient;
    }

    double[] correctDifferentials(double[] differentials) {
        if (USE_AFFINE_CORRECTION) {
            double[] correction = new double[differentials.length];
//            System.arraycopy(differentials, 0, correction, 0, differentials.length);

            if (whichSubstitutionModel > 0) {
                throw new RuntimeException("Not yet implemented");
            }

            EigenDecomposition ed = substitutionModel.getEigenDecomposition();
            int index = findZeroEigenvalueIndex(ed.getEigenValues());

            double[] eigenVectors = ed.getEigenVectors();
            double[] inverseEigenVectors = ed.getInverseEigenVectors();

            double[] qQPlus = getQQPlus(eigenVectors, inverseEigenVectors, index);

            for (int m = 0; m < stateCount; ++m) {
                for (int n = 0; n < stateCount; n++) {
                    double entryMN = 0.0;
                    for (int i = 0; i < stateCount; ++i) {
                        for (int j = 0; j < stateCount; ++j) {
                            if (i == j) {
                                entryMN += differentials[index12(i,j)] *
                                        (1.0 - qQPlus[index12(i,m)]) * qQPlus[index12(n,j)];
                            } else {
                                entryMN += differentials[index12(i,j)] *
                                        - qQPlus[index12(i,m)] * qQPlus[index12(n,j)];
                            }
//                            entryMN += differentials[i * stateCount + j] *
//                                    qQPlus[i * stateCount + m] * qQPlus[n * stateCount + j];
                        }
                    }
                    correction[index12(m,n)] = entryMN;
                }
            }

            System.err.println("diff: " + new WrappedVector.Raw(differentials));
            System.err.println("corr: " + new WrappedVector.Raw(correction));

            for (int i = 0; i < differentials.length; ++i) {
                differentials[i] -= correction[i];
            }

            return differentials;
//            return correction;
        } else {
            return differentials;
        }
    }

    private int findZeroEigenvalueIndex(double[] eigenvalues) {
        for (int i = 0; i < stateCount; ++i) {
            if (eigenvalues[i] == 0) {
                return i;
            }
        }
        return -1;
    }

    private double[] getQQPlus(double[] eigenVectors, double[] inverseEigenVectors, int index) {
        return DifferentiableSubstitutionModelUtil.getQQPlus(eigenVectors, inverseEigenVectors, index, stateCount);
    }

    private int index12(int i, int j) {
        return i * stateCount + j;
    }

    @SuppressWarnings("unused")
    private int index21(int i, int j) {
        return j * stateCount + i;
    }

    private int determineSubstitutionNumber(BranchModel branchModel,
                                            ComplexSubstitutionModel substitutionModel) {

        List<SubstitutionModel> substitutionModels = branchModel.getSubstitutionModels();
        for (int i = 0; i < substitutionModels.size(); ++i) {
            if (substitutionModel == substitutionModels.get(i)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown substitution model");
    }

    private int determineSubstitutionModelCount(BranchModel branchModel) {
        List<SubstitutionModel> substitutionModels = branchModel.getSubstitutionModels();
        return substitutionModels.size();
    }

    // Should maybe be void and just update crossProducts?
    private double[] accumulateAcrossSubstitutionModelInstances(double[] crossProducts) {
        double[] accumulated = new double[crossProducts.length];
        final int length = stateCount * stateCount;

        // TODO first set of entries should be a copy (instead of accumulate)

        for (int i : crossProductAccumulationMap) {
            for (int j = 0; j < length; j++) {
                accumulated[j] += crossProducts[i * length + j];
            }
        }

        return accumulated;
    }

    private void updateCrossProductAccumulationMap() {
        System.err.println("Updating crossProductAccumulationMap");
        List<Integer> matchingModels = new ArrayList<>();
        List<SubstitutionModel> substitutionModels = branchModel.getSubstitutionModels();
        for (int i = 0; i < substitutionModels.size(); ++i) {
            if (substitutionModel == substitutionModels.get(i)) {
                matchingModels.add(i);
            }
        }

        crossProductAccumulationMap = new int[matchingModels.size()];
        for (int i = 0; i < matchingModels.size(); ++i) {
            crossProductAccumulationMap[i] = matchingModels.get(i);
        }
    }

    @Override
    public Likelihood getLikelihood() {
        return treeDataLikelihood;
    }

    @Override
    public int getDimension() {
        return getParameter().getDimension();
    }

    @Override
    public String getReport() {

        StringBuilder sb = new StringBuilder();

        String message = GradientWrtParameterProvider.getReportAndCheckForError(this, 0.0, Double.POSITIVE_INFINITY, getReportTolerance());
        sb.append(message);

        if (DEBUG_CROSS_PRODUCTS) {
            sb.append("\n\tdifferentials: ").append(new WrappedVector.Raw(savedDifferentials, 0, savedDifferentials.length));
        }

        if (COUNT_TOTAL_OPERATIONS) {
            sb.append("\n\tgetCrossProductGradientCount = ").append(gradientCount);
            sb.append("\n\taverageGradientTime = ");
            if (gradientCount > 0) {
                sb.append(totalGradientTime / gradientCount);
            } else {
                sb.append("NA");
            }
            sb.append("\n");
        }

        return  sb.toString();
    }


    Double getReportTolerance() {
        return null;
    }

    // This has not been rigorously tested for epochs that change structure
    protected void handleModelChangedEvent(Model model, Object object, int index) {
        if (model == branchModel) {
            updateCrossProductAccumulationMap();
        }
    }

    public void modelChangedEvent(Model model, Object object, int index) {

    }

    public void modelRestored(Model model) {

    }

    protected static final boolean COUNT_TOTAL_OPERATIONS = false;
    protected static final boolean DEBUG_CROSS_PRODUCTS = false;

    protected double[] savedDifferentials;

    protected long gradientCount = 0;
    protected long totalGradientTime = 0;
}