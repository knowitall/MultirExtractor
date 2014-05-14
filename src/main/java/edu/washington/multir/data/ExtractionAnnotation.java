package edu.washington.multir.data;

/**
 * Representation of ExtractionAnnotation is
 * an instance of an Extraction with a boolean label
 * @author jgilme1
 *
 */
public class ExtractionAnnotation
{
    Extraction e;
    boolean label;

    public ExtractionAnnotation(Extraction e, boolean label)
    {
        this.e = e;
        this.label = label;
    }

    public static ExtractionAnnotation deserialize(String s)
    {
        String[] values = s.split("\t");
        if(values.length != 11)
        {
            throw new IllegalArgumentException("There should be 11 columns of data" + s);
        }

        StringBuilder extractionStringBuilder = new StringBuilder();
        for(int i = 0; i < 10; i++)
        {
            extractionStringBuilder.append(values[i]);
            extractionStringBuilder.append("\t");
        }

        Extraction extr = Extraction.deserialize(extractionStringBuilder.toString().trim());
        String annoLabel = values[10];
        boolean anno = false;
        if(annoLabel.equals("y"))
        {
            anno = true;
        }
        else if(annoLabel.equals("n"))
        {
            anno = false;
        }
        else
        {
            throw new IllegalArgumentException("Label must be either 'y' or 'n'");
        }
        return new ExtractionAnnotation(extr, anno);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(e.arg1.toString());
        sb.append("\t");
        sb.append(e.arg2.toString());
        sb.append("\t");
        sb.append(e.docName);
        sb.append("\t");
        sb.append(e.relation);
        sb.append("\t");
        sb.append(String.valueOf(e.sentNum));
        sb.append("\t");
        sb.append(e.senText);
        sb.append("\t");

        if(label)
            sb.append("y");
        else
            sb.append("n");

        return sb.toString();
    }

    public Extraction getExtraction()
    {
        return e;
    }

    public boolean getLabel()
    {
        return label;
    }
}

//===================================================================
