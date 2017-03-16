/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package org.apache.poi.xssf.extractor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.poi.xssf.XSSFTestDataSamples;
import org.junit.Test;

/**
 * Tests for {@link XSSFBEventBasedExcelExtractor}
 */
public class TestXSSFBEventBasedExcelExtractor {


    protected XSSFEventBasedExcelExtractor getExtractor(String sampleName) throws Exception {
        return new XSSFBEventBasedExcelExtractor(XSSFTestDataSamples.
                openSamplePackage(sampleName));
    }

    /**
     * Get text out of the simple file
     */
    @Test
    public void testGetSimpleText() throws Exception {
        // a very simple file
        XSSFEventBasedExcelExtractor extractor = getExtractor("sample.xlsb");
        extractor.setIncludeCellComments(true);
        extractor.getText();

        String text = extractor.getText();
        assertTrue(text.length() > 0);

        // Check sheet names
        assertTrue(text.startsWith("Sheet1"));
        assertTrue(text.endsWith("Sheet3\n"));

        // Now without, will have text
        extractor.setIncludeSheetNames(false);
        text = extractor.getText();
        String CHUNK1 =
                "Lorem\t111\n" +
                        "ipsum\t222\n" +
                        "dolor\t333\n" +
                        "sit\t444\n" +
                        "amet\t555\n" +
                        "consectetuer\t666\n" +
                        "adipiscing\t777\n" +
                        "elit\t888\n" +
                        "Nunc\t999\n";
        String CHUNK2 =
                "The quick brown fox jumps over the lazy dog\n" +
                        "hello, xssf	hello, xssf\n" +
                        "hello, xssf	hello, xssf\n" +
                        "hello, xssf	hello, xssf\n" +
                        "hello, xssf	hello, xssf\n";
        assertEquals(
                CHUNK1 +
                        "at\t4995\n" +
                        CHUNK2
                , text);

    }


    /**
     * Test text extraction from text box using getShapes()
     *
     * @throws Exception
     */
    @Test
    public void testShapes() throws Exception {
        XSSFEventBasedExcelExtractor ooxmlExtractor = getExtractor("WithTextBox.xlsb");

        try {
            String text = ooxmlExtractor.getText();

            assertTrue(text.indexOf("Line 1") > -1);
            assertTrue(text.indexOf("Line 2") > -1);
            assertTrue(text.indexOf("Line 3") > -1);
        } finally {
            ooxmlExtractor.close();
        }
    }

}