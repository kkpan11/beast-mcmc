/*
 * BayesianBridgeShrinkageOperator.java
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

package dr.inference.operators.shrinkage;

import dr.inference.distribution.ExponentialTiltedStableDistribution;
import dr.inference.distribution.shrinkage.BayesianBridgeStatisticsProvider;
import dr.inference.model.Parameter;
import dr.inference.operators.GibbsOperator;
import dr.inference.operators.SimpleMCMCOperator;
import dr.math.distributions.GammaDistribution;

import static dr.inferencexml.operators.shrinkage.BayesianBridgeShrinkageOperatorParser.BAYESIAN_BRIDGE_PARSER;

/**
 * @author Marc A. Suchard
 * @author Akihiko Nishimura
 */

public class BayesianBridgeShrinkageOperator extends SimpleMCMCOperator implements GibbsOperator {

    private final BayesianBridgeStatisticsProvider provider;
    private final Parameter globalScale;
    private final Parameter localScale;
    private final Parameter regressionExponent;
    private final Parameter mask;
    private final int dim;
    private final int effectiveDim;

    private final GammaDistribution globalScalePrior;

    public BayesianBridgeShrinkageOperator(BayesianBridgeStatisticsProvider bayesianBridge,
                                           GammaDistribution globalScalePrior,
                                           Parameter mask,
                                           double weight) {

        setWeight(weight);

        this.provider = bayesianBridge;
        this.globalScale = bayesianBridge.getGlobalScale();
        this.localScale = bayesianBridge.getLocalScale();
        this.regressionExponent = bayesianBridge.getExponent();
        this.mask = mask;
        this.dim = bayesianBridge.getDimension();
        this.effectiveDim = getEffectiveDim();


        this.globalScalePrior = globalScalePrior;
    }

    private int getEffectiveDim() {
        int effectiveDim = 0;
        for (int i = 0; i < dim; ++i) {
            if (random(i)) {
                ++effectiveDim;
            }
        }
        return effectiveDim;
    }

    @Override
    public String getOperatorName() {
        return BAYESIAN_BRIDGE_PARSER;
    }

    @Override
    public double doOperation() {

        if (globalScalePrior != null) {
            sampleGlobalScale(); // Order matters
        }

        if (localScale != null) {
            sampleLocalScale();
        }

        return 0;
    }

    public double drawGlobalScale(double priorShape, double priorScale, double exponent, double effectiveDim, double absSumCoefficients) {
        double shape = effectiveDim / exponent;
        double rate = absSumCoefficients;

        if (priorShape > 0.0) {
            shape += priorShape;
            rate += 1.0 / priorScale;
        }

        double phi = GammaDistribution.nextGamma(shape, 1.0 / rate);
        double draw = Math.pow(phi, -1.0 / exponent);

        return draw;
    }

    public void sampleGlobalScale() {
        double draw = drawGlobalScale(globalScalePrior.getShape(), globalScalePrior.getScale(), regressionExponent.getParameterValue(0), effectiveDim, absSumBeta());

        globalScale.setParameterValue(0, draw);

        //        # Conjugate update for phi = 1 / gshrink ** reg_exponent
        //        shape = beta_with_shrinkage.size / reg_exponent
        //        scale = 1 / np.sum(np.abs(beta_with_shrinkage) ** reg_exponent)
        //        phi = self.rg.np_random.gamma(shape, scale=scale)
        //        gshrink = 1 / phi ** (1 / reg_exponent)
        //
        //   To update the global scale parameter τ, we work directly with the exponential-power density, marginalizing out the latent variables {ωj,uj}. This is a crucial source of efficiency in the bridge MCMC, and leads to the favorable mixing evident in Figure 1. From (1), observe that the posterior for ν ≡ τ−α, given β, is conditionally independent of y, and takes the form
        //
        //    p(ν | β) propto νp/α exp(−ν |βj|α) p(ν).
        //    j=1
        //    Therefore if ν has a Gamma(c, d) prior, its conditional posterior will also be a gamma distribution, with hyperparameters c⋆ = c+p/α and d⋆ = d+pj=1 |βj|α. To sample τ, simply draw ν from this gamma distribution, and use the transformation τ = ν−1/α.
    }

    private boolean random(int index) {
        return mask == null || mask.getParameterValue(index) == 1.0;
    }

    private double absSumBeta() {

        double exponent = regressionExponent.getParameterValue(0);
        double sum = 0.0;
        for (int i = 0; i < dim; ++i) {
            if (random(i)) {
                sum += Math.pow(Math.abs(provider.getCoefficient(i)), exponent);
            }
        }

        return sum;
    }

    public double drawSingleLocalScale(double global, double exponent, double coefficient) {
        double draw = ExponentialTiltedStableDistribution.nextTiltedStable(
                exponent / 2, Math.pow(coefficient / global, 2));
        draw = Math.sqrt(1 / (2 * draw));
        return draw;
    }

    private void sampleLocalScale() {

        final double exponent = regressionExponent.getParameterValue(0);
        final double global = globalScale.getParameterValue(0);

        for (int i = 0; i < dim; ++i) {

            if (random(i)) {
                double draw = drawSingleLocalScale(global, exponent, provider.getCoefficient(i));
                localScale.setParameterValueQuietly(i, draw);
            }
        }

        localScale.fireParameterChangedEvent();

        //
        //        lshrink_sq = 1 / np.array([
        //            2 * self.rg.tilted_stable(reg_exponent / 2, (beta_j / gshrink) ** 2)
        //            for beta_j in beta_with_shrinkage
        //        ])
        //        lshrink = np.sqrt(lshrink_sq)

        //        if np.any(lshrink == 0):
        //            warn_message_only(
        //                "Local shrinkage parameter under-flowed. Replacing with a small number.")
        //            lshrink[lshrink == 0] = 10e-16
        //        elif np.any(np.isinf(lshrink)):
        //            warn_message_only(
        //                "Local shrinkage parameter under-flowed. Replacing with a large number.")
        //            lshrink[np.isinf(lshrink)] = 2.0 / gshrink
        //
        //        return lshrink
    }
}

//    def slice_sample_global_shrinkage(
//            self, gshrink, beta_with_shrinkage, global_scale, reg_exponent):
//        """ Slice sample phi = 1 / gshrink ** reg_exponent. """
//
//        n_update = 10 # Slice sample for multiple iterations to ensure good mixing.
//
//        # Initialize a gamma distribution object.
//        shape = (beta_with_shrinkage.size + 1) / reg_exponent
//        scale = 1 / np.sum(np.abs(beta_with_shrinkage) ** reg_exponent)
//        gamma_rv = sp.stats.gamma(shape, scale=scale)
//
//        phi = 1 / gshrink
//        for i in range(n_update):
//            u = self.rg.np_random.uniform() \
//                / (1 + (global_scale * phi ** (1 / reg_exponent)) ** 2)
//            upper = (np.sqrt(1 / u - 1) / global_scale) ** reg_exponent
//                # Invert the half-Cauchy density.
//            phi = gamma_rv.ppf(gamma_rv.cdf(upper) * self.rg.np_random.uniform())
//            if np.isnan(phi):
//                # Inverse CDF method can fail if the current conditional
//                # distribution is drastically different from the previous one.
//                # In this case, ignore the slicing variable and just sample from
//                # a Gamma.
//                phi = gamma_rv.rvs()
//        gshrink = 1 / phi ** (1 / reg_exponent)
//
//        return gshrink
