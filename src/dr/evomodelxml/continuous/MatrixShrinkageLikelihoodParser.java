/*
 * MatrixShrinkageLikelihoodParser.java
 *
 * Copyright © 2002-2024 the BEAST Development Team
 * http://beast.community/about
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
 *
 */

package dr.evomodelxml.continuous;

import dr.evomodel.continuous.MatrixShrinkageLikelihood;
import dr.inference.distribution.shrinkage.BayesianBridgeLikelihood;
import dr.inference.model.MatrixParameterInterface;
import dr.inferencexml.distribution.shrinkage.BayesianBridgeLikelihoodParser;
import dr.xml.*;


/**
 * @author Gabriel Hassler
 */


public class MatrixShrinkageLikelihoodParser extends AbstractXMLObjectParser {
    public static final String MATRIX_SHRINKAGE = "matrixShrinkageLikelihood";
    private static final String ROW_PRIORS = "rowPriors";


    @Override
    public Object parseXMLObject(XMLObject xo) throws XMLParseException {

        String name = xo.getAttribute("id", MatrixShrinkageLikelihood.class.toString());

        MatrixParameterInterface loadings = (MatrixParameterInterface) xo.getChild(MatrixParameterInterface.class);
        XMLObject rpxo = xo.getChild(ROW_PRIORS);

        BayesianBridgeLikelihood[] rowModels = new BayesianBridgeLikelihood[loadings.getColumnDimension()];

        int counter = 0;


        for (int i = 0; i < rpxo.getChildCount(); i++) {
            Object cxo = rpxo.getChild(i);
            if (cxo instanceof BayesianBridgeLikelihood) {
                rowModels[i] = (BayesianBridgeLikelihood) cxo;
                counter++;
            } else {
                throw new XMLParseException("The " + ROW_PRIORS + " element should only have "
                        + BayesianBridgeLikelihoodParser.BAYESIAN_BRIDGE + " child elements.");
            }
        }

        if (counter != loadings.getColumnDimension()) {
            throw new XMLParseException(
                    "The number of " + BayesianBridgeLikelihoodParser.BAYESIAN_BRIDGE +
                            " in " + ROW_PRIORS + " must be the same as the number of rows in the matrix " +
                            loadings.getParameterName() + ".\nThere are currently " + counter + " " +
                            BayesianBridgeLikelihoodParser.BAYESIAN_BRIDGE + " elements and " +
                            loadings.getRowDimension() + " rows in " + loadings.getParameterName() + ".");
        }

        for (BayesianBridgeLikelihood model : rowModels) {
            if (model.getLocalScale().getDimension() != loadings.getRowDimension()) {
                throw new XMLParseException("The dimension of the " + BayesianBridgeLikelihoodParser.LOCAL_SCALE +
                        " must match the number of columns in the loadings matrix");
            }
        }


        return new MatrixShrinkageLikelihood(name, loadings, rowModels);
    }

    @Override
    public XMLSyntaxRule[] getSyntaxRules() {
        return new XMLSyntaxRule[]{
                new ElementRule(MatrixParameterInterface.class),
                new ElementRule(ROW_PRIORS, new XMLSyntaxRule[]{
                        new ElementRule(BayesianBridgeLikelihood.class, 1, Integer.MAX_VALUE)
                })
        };
    }

    @Override
    public String getParserDescription() {
        return "Bayesian bridge shrinkage prior on the loadings matrix of a latent factor model";
    }

    @Override
    public Class getReturnType() {
        return MatrixShrinkageLikelihood.class;
    }

    @Override
    public String getParserName() {
        return MATRIX_SHRINKAGE;
    }
}
