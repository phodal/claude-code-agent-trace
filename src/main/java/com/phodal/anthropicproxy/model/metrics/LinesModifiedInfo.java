package com.phodal.anthropicproxy.model.metrics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Information about lines modified in an edit operation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinesModifiedInfo {
    private int linesAdded;      // lines in new content
    private int linesRemoved;    // lines in old content
    private String filePath;     // affected file path
    
    /**
     * Get net change (positive = more lines added, negative = more lines removed)
     */
    public int getNetChange() {
        return linesAdded - linesRemoved;
    }
    
    /**
     * Get absolute change (total lines touched)
     */
    public int getAbsoluteChange() {
        return Math.max(linesAdded, linesRemoved);
    }
    
    public static LinesModifiedInfo empty() {
        return new LinesModifiedInfo(0, 0, null);
    }
    
    public static LinesModifiedInfo ofNew(int lines, String filePath) {
        return new LinesModifiedInfo(lines, 0, filePath);
    }
    
    public static LinesModifiedInfo ofReplace(int newLines, int oldLines, String filePath) {
        return new LinesModifiedInfo(newLines, oldLines, filePath);
    }
}
