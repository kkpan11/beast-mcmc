package dr.inference.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by max on 11/4/14.
 */
public class TransposedBlockUpperTriangularMatrixParameter extends BlockUpperTriangularMatrixParameter{
    public TransposedBlockUpperTriangularMatrixParameter(String name, Parameter[] params) {
        super(name, params);



        int colDim=params[params.length-1].getSize();
//        int rowDim=params.length;

//        for(int i=0; i<colDim; i++){
//            if(i<rowDim)
//            {params[i].setDimension(i+1);
//                this.addParameter(params[i]);}
//            else
//            {params[i].setDimension(rowDim);
//                this.addParameter(params[i]);
////                System.err.print(colDim-rowDim+i+1);
////                System.err.print("\n");
//            }
//        }
        this.colDim=colDim;
    }


    public static TransposedBlockUpperTriangularMatrixParameter recast(String name, CompoundParameter compoundParameter) {
        final int count = compoundParameter.getParameterCount();
        Parameter[] parameters = new Parameter[count];
        for (int i = 0; i < count; ++i) {
            parameters[i] = compoundParameter.getParameter(i);
        }
        return new TransposedBlockUpperTriangularMatrixParameter(name, parameters);
    }

    public double getParameterValue(int row, int col){
        if(col>row){
            return 0;
        }
        else{
            return super.getParameterValue(row, col+row);
        }
    }



    public Parameter getParameter(int index) {
        if (slices == null) {
            // construct vector_slices
            slices = new ArrayList<Parameter>();
            for (int i = 0; i < getColumnDimension(); ++i) {
                VectorSliceParameter thisSlice = new VectorSliceParameter(getParameterName() + "." + i, i);
                for (int j = i; j < getRowDimension(); ++j) {
                    thisSlice.addParameter(super.getParameter(j));
                }
                slices.add(thisSlice);
            }
        }
        return slices.get(index);
    }

    public int getRowDimension(){
        System.out.println(getParameterCount());
        return getParameterCount();
    }

    public int getColumnDimension(){
        return colDim;
    }

    int colDim;
    private List<Parameter> slices = null;
}