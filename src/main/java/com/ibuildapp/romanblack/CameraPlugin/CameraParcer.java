/****************************************************************************
*                                                                           *
*  Copyright (C) 2014-2015 iBuildApp, Inc. ( http://ibuildapp.com )         *
*                                                                           *
*  This file is part of iBuildApp.                                          *
*                                                                           *
*  This Source Code Form is subject to the terms of the iBuildApp License.  *
*  You can obtain one at http://ibuildapp.com/license/                      *
*                                                                           *
****************************************************************************/
package com.ibuildapp.romanblack.CameraPlugin;

import android.util.Log;
import android.util.Xml;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This class using for parsing module xml data.
 */
public class CameraParcer {

    /**
     * Constructs new CameraParser instance.
     * @param xml - module xml data to parse
     */
    public CameraParcer(String xml) {
        this.xmlData = xml;
    }
    
    private String xmlData = "";
    private String buttonType = "";
    private String buttonLabel = "";
    private String eMail = "";

    /**
     * Parses module data that was set in constructor.
     */
    public void parse() {
        CameraHandler handler = new CameraHandler();

        try {
            Xml.parse(xmlData, handler);
        } catch (Exception e) {
            Log.w(e.toString(), e.getMessage());
        }

        this.buttonType = handler.getType();
        this.buttonLabel = handler.getLabel();
        this.eMail = handler.geteMail();
    }

    /**
     * Returns the button type (send on email or share).
     * @return the button type
     */
    public String getButtonType() {
        return buttonType;
    }

    /**
     * Returns the share button label.
     * @return the label
     */
    public String getButtonLabel() {
        return buttonLabel;
    }

    /**
     * Returns the email to send taken ticture.
     * @return the email string
     */
    public String getEmail() {
        return this.eMail;
    }

    /**
     * Sax handler that handle XML configuration tags and prepare module data structure.
     */
    class CameraHandler extends DefaultHandler {

        private boolean inButton = false;
        private boolean inLabel = false;
        private boolean inType = false;
        private boolean inEmail = false;
        private String label = "";
        private String Type = "";
        private String eMail = "";

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);
            if (localName.equalsIgnoreCase("button")) {
                inButton = true;
            } else if (localName.equalsIgnoreCase("label")) {
                inLabel = true;
            } else if (localName.equalsIgnoreCase("type")) {
                inType = true;
            } else if (localName.equalsIgnoreCase("email")) {
                inEmail = true;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            super.characters(ch, start, length);
            if (inButton && inLabel) {
                String str = new String(ch, start, length);
                if (!str.equals("\n") && !str.equals(" ")) {
                    label = str;
                }
            } else if (inButton && inType) {
                String str = new String(ch, start, length);
                if (!str.equals("\n") && !str.equals(" ")) {
                    Type = str;
                }
            } else if (inButton && inEmail) {
                String str = new String(ch, start, length);
                str = str.trim();
                if (!str.equals("\n") && !str.equals(" ") && !str.equals("")) {
                    eMail = str;
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            super.endElement(uri, localName, qName);
            if (localName.equalsIgnoreCase("button")) {
                inButton = false;
            } else if (localName.equalsIgnoreCase("label")) {
                inLabel = false;
            } else if (localName.equalsIgnoreCase("type")) {
                inType = false;
            } else if (localName.equalsIgnoreCase("type")) {
                inEmail = false;
            }
        }

        /**
         * Returns the share button label.
         * @return the label
         */
        public String getLabel() {
            return label;
        }

        /**
         * Returns the button type (send on email or share).
         * @return the button type
         */
        public String getType() {
            return Type;
        }

        /**
         * Returns the email to send taken ticture.
         * @return the email string
         */
        public String geteMail() {
            return eMail;
        }
    }
}
