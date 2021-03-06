/*
 * Copyright 2012 Kevan Dunsmore.  All rights reserved.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.sunsprinter.diffunit.core.comparison;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.sunsprinter.diffunit.core.context.ITestingContext;


/**
 * AbstractFileComparer
 *
 * @author Kevan Dunsmore
 * @created 2011/11/13
 */
public abstract class AbstractFileComparer implements IFileComparer
{
    private ITestingContext _testingContext;
    private Collection<File> _filesToCompare = new LinkedList<File>();


    protected Collection<File> getFilesToCompare()
    {
        return _filesToCompare;
    }


    protected void setFilesToCompare(final Collection<File> filesToCompare)
    {
        _filesToCompare = filesToCompare;
    }


    protected ITestingContext getTestingContext()
    {
        return _testingContext;
    }


    public void setTestingContext(final ITestingContext testingContext)
    {
        _testingContext = testingContext;
    }


    @Override
    public void registerFileToCompare(final File file)
    {
        getFilesToCompare().add(file);
    }


    @Override
    public void compareAllFiles() throws Exception
    {
        // Get the input location type from the annotation on the class.  If we don't have one we default to CLASSPATH.
        final DiffUnitInputLocation classInputLocationAnnotation =
                getTestingContext().getTestClass().getAnnotation(DiffUnitInputLocation.class);
        final InputLocationType locationType = classInputLocationAnnotation == null ? InputLocationType.CLASSPATH : classInputLocationAnnotation.locationType();
        final String classInputLocation = classInputLocationAnnotation == null ? null : classInputLocationAnnotation.location();

        // Figure out the input location path by checking to see what our location type is.
        final String inputLocation;
        if (locationType == InputLocationType.CLASSPATH)
        {
            inputLocation = classInputLocation == null ? String.format("/%s/%s",
                                                                       getTestingContext().getTestClass().getSimpleName(),
                                                                       getTestingContext().getTestName()) : classInputLocation;
        }
        else
        {
            inputLocation = classInputLocation == null ? String.format("src/test/resources/%s/%s",
                                                                       getTestingContext().getTestClass().getSimpleName(),
                                                                       getTestingContext().getTestName()) : classInputLocation;
        }

        // Go through all the files we wrote and compare them against the known good ones stored in the input location.
        for (final File generatedFile : getFilesToCompare())
        {
            final String knownGoodTextFileName = generatedFile.getName();
            InputStream knownGoodInputStream = null;
            InputStream generatedInputStream = null;
            try
            {
                final String inputStreamLocation = inputLocation + "/" + knownGoodTextFileName;
                if (locationType == InputLocationType.CLASSPATH)
                {

                    knownGoodInputStream = getClass().getResourceAsStream(inputStreamLocation);
                }
                else
                {
                    try
                    {
                        knownGoodInputStream = new FileInputStream(inputStreamLocation);
                    }
                    catch (final FileNotFoundException e)
                    {
                        // Do nothing here.  We handle not being able to find the input file below.
                    }
                }

                if (knownGoodInputStream == null)
                {
                    // There's no input file.  We create a dummy one for comparison purposes.  This lets the
                    // developer see differences for all files rather than just bailing here.
                    knownGoodInputStream = new ByteArrayInputStream(
                            String.format("Input file %s not found.", inputStreamLocation).getBytes());
                }

                final Collection<String> knownGoodLines = IOUtils.readLines(knownGoodInputStream);

                generatedInputStream = new FileInputStream(generatedFile);
                final Collection<String> generatedLines = IOUtils.readLines(generatedInputStream);

                assertEqual(knownGoodLines,
                            inputStreamLocation,
                            locationType,
                            generatedLines,
                            generatedFile);
            }
            finally
            {
                IOUtils.closeQuietly(knownGoodInputStream);
                IOUtils.closeQuietly(generatedInputStream);
            }

        }
    }


    protected void assertEqual(final Collection<String> knownGoodLines,
                               final String knownGoodPath,
                               final InputLocationType knownGoodLocationType,
                               final Collection<String> generatedLines,
                               final File generatedFile) throws Exception
    {
        final String knownGoodFullPath;
        if (knownGoodLocationType == InputLocationType.CLASSPATH)
        {
            knownGoodFullPath = knownGoodPath;
        }
        else
        {
            knownGoodFullPath = new File(knownGoodPath).getAbsolutePath();
        }

        final StringBuilder errorBuilder = new StringBuilder();

        if (knownGoodLines.size() != generatedLines.size())
        {
            errorBuilder.append(String.format("The number of lines is different.  The known good file has %d lines.  " +
                                              "The generated file has %d lines.  ",
                                              knownGoodLines.size(), generatedLines.size()));
        }

        int i = 1;
        String firstDifferenceMessage = null;
        final Iterator<String> knownGoodIterator = knownGoodLines.iterator();
        final Iterator<String> generatedIterator = generatedLines.iterator();
        while (firstDifferenceMessage == null && knownGoodIterator.hasNext() && generatedIterator.hasNext())
        {
            final String knownGoodLine = knownGoodIterator.next();
            final String generatedLine = generatedIterator.next();
            if (!knownGoodLine.equals(generatedLine))
            {
                firstDifferenceMessage =
                        String.format("First difference detected at line number %d, position %d.\n\n" +
                                      "Known Good: %s\n" +
                                      "Generated : %s\n",
                                      i,
                                      StringUtils.indexOfDifference(knownGoodLine,
                                                                    generatedLine),
                                      knownGoodLine,
                                      generatedLine);
            }
            i++;
        }
        if (firstDifferenceMessage != null)
        {
            errorBuilder.append(firstDifferenceMessage);
        }

        if (errorBuilder.length() != 0)
        {
            fail(String.format("Generated file %s does not match known good file %s.  %s",
                               generatedFile.getAbsolutePath(), knownGoodFullPath, errorBuilder));
        }
    }


    protected abstract void fail(final String message) throws Exception;
}
