package edu.unc.bioinf.ubu.sam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.samtools.Cigar;
import net.sf.samtools.CigarElement;
import net.sf.samtools.CigarOperator;
import net.sf.samtools.SAMRecord;

/**
 * Represents a Block of a read.  i.e. A Cigar of 15M5I30M would be represented
 * by 3 ReadBlocks.
 * 
 * @author Lisle Mose (lmose at unc dot edu)
 */
public class ReadBlock {
    private int readStart;
    private int referenceStart;
    private int length;
    private CigarOperator type;
    
    ReadBlock(int readStart, int referenceStart, int length, CigarOperator type) {
        this.readStart = readStart;
        this.referenceStart = referenceStart;
        this.length = length;
        this.type = type;
    }

    public int getReadStart() {
        return readStart;
    }

    public int getReferenceStart() {
        return referenceStart;
    }
    
    public int getReferenceStop() {
    	//TODO: This doesn't appear to be correct for delete block type
        return referenceStart + length - 1;
    }

    public int getLength() {
        return length;
    }
    
    public int getReferenceLength() {
    	int refLength = 0;
    	
    	if (type != CigarOperator.D) {
    		refLength = length;
    	}
    	
    	return refLength;
    }

    public CigarOperator getType() {
        return type;
    }
    
    /**
     * Returns a ReadBlock as a subset of this readblock
     */
    public ReadBlock getSubBlock(int accumulatedLength, int positionInRead, int maxLength) {
    	
    	// zero base + zero base - 1 base  + 1 = zero base
    	int positionInBlock = positionInRead + accumulatedLength - readStart + 1;
    	
    	if ((type == CigarOperator.N) || (type == CigarOperator.D)) {
    		// Intron / Deletion: return entire block
        	return new ReadBlock(accumulatedLength+1, referenceStart + positionInBlock, length-positionInBlock, type);
    	} else if (type == CigarOperator.S) {
    		// Soft clipped blocks begin at next block's referenceStart.  No need to change it.
    		return new ReadBlock(accumulatedLength+1, referenceStart, Math.min(maxLength, length - positionInBlock), type);
    	} else {
    		return new ReadBlock(accumulatedLength+1, referenceStart + positionInBlock, Math.min(maxLength, length - positionInBlock), type);
    	}
    }
    
    public static String toCigarString(List<ReadBlock> blocks) {
    	StringBuffer cigar = new StringBuffer();
    	
    	for (ReadBlock block : blocks) {
    		cigar.append(block.getLength());
    		cigar.append(block.getType());
    	}
    	
    	return cigar.toString();
    }
    
    //TODO - Move elsewhere and make non-static
    public static List<ReadBlock> getReadBlocks(SAMRecord read) {    
        final Cigar cigar = read.getCigar();
        if (cigar == null) return Collections.emptyList();

        final List<ReadBlock> readBlocks = new ArrayList<ReadBlock>();
        int readBase = 1;
        int refBase  = read.getAlignmentStart();

        for (final CigarElement e : cigar.getCigarElements()) {
            
            readBlocks.add(new ReadBlock(readBase, refBase, e.getLength(), e.getOperator()));
            
            switch (e.getOperator()) {
//                case H : break; // ignore hard clips
//                case P : break; // ignore pads
                case S : readBase += e.getLength();
//                System.out.println(read.getReadName());
                break; // soft clip read bases
                case N : refBase += e.getLength(); break;  // reference skip
                case D : refBase += e.getLength(); break;
                case I : readBase += e.getLength(); break;
                case M :
//                case EQ :
//                case X :
                    final int length = e.getLength();
                    readBase += length;
                    refBase  += length;
                    break;
                default : throw new IllegalStateException(
                        "Case statement didn't deal with cigar op: " + e.getOperator() +
                        " for read: [" + read.getReadName() + "]");
            }
        }
        
        return Collections.unmodifiableList(readBlocks);
    }
}
