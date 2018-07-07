/*
 * Copyright (C) 2018 Information Management Services, Inc.
 */
package com.imsweb.naaccrxml.sas;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * This class is a simplified NAACCR XML reader.
 * <br/><br/>
 * It doesn't use an XML parser; it expects the XML to be well formatted (one item per line) and uses regular expressions to find the values.
 */
public class SasXmlReader {

    private File _xmlFile;

    private BufferedReader _reader;

    private boolean _inPatient, _inTumor;

    private Map<String, String> _naaccrDataValues = new HashMap<>(), _patientValues = new HashMap<>(), _tumorValues = new HashMap<>();

    public SasXmlReader(String xmlPath) {
        _xmlFile = new File(xmlPath);
    }

    public int nextRecord() throws IOException {
        if (_reader == null)
            _reader = SasUtils.createReader(_xmlFile);

        _tumorValues.clear();

        String currentKey = null;
        StringBuilder currentVal = null;

        String line = _reader.readLine();
        while (line != null) {
            int itemIdx = line.indexOf("<Item");
            if (itemIdx > -1) {
                int naaccrIdStart = line.indexOf('\"', itemIdx + 1);
                if (naaccrIdStart == -1)
                    naaccrIdStart = line.indexOf('\'', itemIdx + 1);
                if (naaccrIdStart == -1)
                    throw new IOException("Unable to find start of NAACCR ID attribute for: " + line);
                int naaccrIdEnd = line.indexOf('\"', naaccrIdStart + 1);
                if (naaccrIdEnd == -1)
                    naaccrIdEnd = line.indexOf('\'', naaccrIdStart + 1);
                if (naaccrIdEnd == -1)
                    throw new IOException("Unable to find end of NAACCR ID attribute for: " + line);

                String key = line.substring(naaccrIdStart + 1, naaccrIdEnd);

                int valueStart = line.indexOf('>', naaccrIdEnd + 1);
                if (valueStart == -1)
                    throw new IOException("Unable to find start of value for: " + line);
                int valueEnd = line.indexOf('<', valueStart + 1);
                if (valueEnd == -1) { // could be the start of a multi-line value...
                    currentKey = key;
                    currentVal = new StringBuilder(line.substring(valueStart + 1));
                }

                // adjust for CDATA sections
                if (valueEnd == valueStart + 1 && line.charAt(valueEnd + 1) == '!' && line.charAt(valueEnd + 2) == '[') {
                    valueStart = line.indexOf('[', valueStart + 4);
                    if (valueStart == -1)
                        throw new IOException("Unable to find start of value for: " + line);
                    valueEnd = line.indexOf(']', valueStart + 1);
                    if (valueEnd == -1) { // could be the start of a multi-line value...
                        currentKey = key;
                        currentVal = new StringBuilder(line.substring(valueStart + 1));
                    }
                }

                if (currentVal == null) {
                    String val = line.substring(valueStart + 1, valueEnd);
                    if (_inPatient)
                        _patientValues.put(key, val);
                    else if (_inTumor)
                        _tumorValues.put(key, val);
                    else
                        _naaccrDataValues.put(key, val);
                }
            }
            else if (line.contains("<Patient>")) {
                if (currentVal != null)
                    throw new IOException("Unable to find end of value for " + currentKey);
                _inPatient = true;
                _inTumor = false;
            }
            else if (line.contains("<Tumor>")) {
                if (currentVal != null)
                    throw new IOException("Unable to find end of value for " + currentKey);
                _inPatient = false;
                _inTumor = true;
            }
            else {
                int endIdx = line.indexOf("</");
                if (endIdx > -1) {
                    if (line.indexOf("Patient>", endIdx) > -1) {
                        if (currentVal != null)
                            throw new IOException("Unable to find end of value for " + currentKey);
                        _inPatient = false;
                        _patientValues.clear();
                    }
                    else if (line.indexOf("Tumor>", endIdx) > -1) {
                        if (currentVal != null)
                            throw new IOException("Unable to find end of value for " + currentKey);
                        _tumorValues.putAll(_naaccrDataValues);
                        _tumorValues.putAll(_patientValues);
                        break;
                    }
                    else if (line.indexOf("Item>", endIdx) > -1) {
                        if (currentVal != null) {
                            // adjust end value for CDATA
                            if (endIdx > 3 && line.charAt(endIdx - 1) == '>' && line.charAt(endIdx - 2) == ']' && line.charAt(endIdx - 3) == ']')
                                endIdx = endIdx - 3;
                            currentVal.append("::").append(line, 0, endIdx);
                            if (_inPatient)
                                _patientValues.put(currentKey, currentVal.toString());
                            else if (_inTumor)
                                _tumorValues.put(currentKey, currentVal.toString());
                            else
                                _naaccrDataValues.put(currentKey, currentVal.toString());
                            currentKey = null;
                            currentVal = null;
                        }
                    }
                    else if (line.indexOf("NaaccrData>", endIdx) > -1) {
                        if (currentVal != null)
                            throw new IOException("Unable to find end of value for " + currentKey);
                        return 0;
                    }
                }
                else if (currentVal != null)
                    currentVal.append("::").append(line);
            }

            line = _reader.readLine();
        }

        return 1;
    }

    public String getValue(String naaccrId) {
        return Objects.toString(_tumorValues.get(naaccrId), "");
    }

    public void close() {
        try {
            if (_reader != null)
                _reader.close();
        }
        catch (IOException e) {
            // ignored
        }
    }
}
