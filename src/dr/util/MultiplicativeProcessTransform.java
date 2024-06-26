/*
 * MultiplicativeProcessTransform.java
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

package dr.util;

import dr.inference.model.Parameter;
import dr.inference.model.TransformedMultivariateParameter;
import dr.xml.AbstractXMLObjectParser;
import dr.xml.XMLObject;
import dr.xml.XMLParseException;
import dr.xml.XMLSyntaxRule;

public class MultiplicativeProcessTransform extends Transform.MultivariateTransform {

    private static final String MULTIPLICATIVE_PROCESS = "multiplicativeProcess";

    private final int dim;

    public MultiplicativeProcessTransform(int dim) {
        super(dim);
        this.dim = dim;
    }

    @Override
    public double[] inverse(double[] values, int from, int to, double sum) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public double[] gradient(double[] values, int from, int to) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public double[] gradientInverse(double[] values, int from, int to) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public String getTransformName() {
        return MULTIPLICATIVE_PROCESS;
    }

    @Override
    protected double[] transform(double[] values) {
        double mult = 1;
        double[] transformedValues = new double[dim];
        for (int i = 0; i < dim; i++) {
            mult *= values[i];
            transformedValues[i] = mult;
        }
        return transformedValues;
    }

    @Override
    protected double[] inverse(double[] values) {
        double[] originalValues = new double[dim];
        originalValues[0] = values[0];
        for (int i = 1; i < dim; i++) {
            originalValues[i] = values[i] / values[i - 1];
        }
        return originalValues;
    }

    @Override
    protected double getLogJacobian(double[] values) {
        throw new RuntimeException("not implemented");
    }

    @Override
    protected double[] getGradientLogJacobianInverse(double[] values) {
        return new double[0];
    }

    @Override
    public double[][] computeJacobianMatrixInverse(double[] values) {
        return new double[0][];
    }

    @Override
    protected boolean isInInteriorDomain(double[] values) {
        return false;
    }

    public static final AbstractXMLObjectParser PARSER = new AbstractXMLObjectParser() {
        @Override
        public TransformedMultivariateParameter parseXMLObject(XMLObject xo) throws XMLParseException {
            final String name = xo.hasId() ? xo.getId() : null;
            Parameter param = (Parameter) xo.getChild(Parameter.class);
            int dim = param.getDimension();
            MultiplicativeProcessTransform transform = new MultiplicativeProcessTransform(dim);
            return new TransformedMultivariateParameter(param, transform);
        }

        @Override
        public XMLSyntaxRule[] getSyntaxRules() {
            return new XMLSyntaxRule[0];
        }

        @Override
        public String getParserDescription() {
            return null;
        }

        @Override
        public Class getReturnType() {
            return TransformedMultivariateParameter.class;
        }

        @Override
        public String getParserName() {
            return "multiplicativeParameter";
        }
    };
}
