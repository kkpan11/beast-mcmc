/*
 * CoalescentTreeIntervalStatisticParser.java
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

/**
 * @author Guy Baele
 */

package dr.evomodelxml.coalescent;

import dr.evolution.tree.Tree;
import dr.evomodel.coalescent.CoalescentTreeIntervalStatistic;
import dr.xml.AbstractXMLObjectParser;
import dr.xml.ElementRule;
import dr.xml.XMLObject;
import dr.xml.XMLParseException;
import dr.xml.XMLSyntaxRule;


public class CoalescentTreeIntervalStatisticParser extends AbstractXMLObjectParser {

    public static final String COALESCENT_TREE_INTERVAL_STATISTIC = "coalescentTreeIntervalStatistic";

    public String getParserDescription() {
        return "";
    }

    public Class getReturnType() {
        return CoalescentTreeIntervalStatistic.class;
    }

    public XMLSyntaxRule[] getSyntaxRules() {
        return new XMLSyntaxRule[]{
                new ElementRule(Tree.class),
        };
    }

    public Object parseXMLObject(XMLObject xo) throws XMLParseException {
    	Tree tree = (Tree) xo.getChild(Tree.class);
        return new CoalescentTreeIntervalStatistic(tree);
    }

    public String getParserName() {
        return COALESCENT_TREE_INTERVAL_STATISTIC;
    }

}
