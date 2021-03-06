/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.ivy.osgi.obr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;

import org.apache.ivy.core.cache.CacheResourceOptions;
import org.apache.ivy.core.event.EventManager;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.osgi.obr.xml.OBRXMLParser;
import org.apache.ivy.osgi.repo.RelativeURLRepository;
import org.apache.ivy.osgi.repo.RepoDescriptorBasedResolver;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.file.FileRepository;
import org.apache.ivy.plugins.repository.url.URLResource;
import org.xml.sax.SAXException;

public class OBRResolver extends RepoDescriptorBasedResolver {

    private String repoXmlURL;

    private String repoXmlFile;

    public void setRepoXmlFile(String repositoryXmlFile) {
        this.repoXmlFile = repositoryXmlFile;
    }

    public void setRepoXmlURL(String repositoryXmlURL) {
        this.repoXmlURL = repositoryXmlURL;
    }

    protected void init() {
        if (repoXmlFile != null && repoXmlURL != null) {
            throw new RuntimeException("The OBR repository resolver " + getName()
                    + " couldn't be configured: repoXmlFile and repoXmlUrl cannot be set both");
        }
        if (repoXmlFile != null) {
            File f = new File(repoXmlFile);
            setRepository(new FileRepository(f.getParentFile()));
            loadRepoFromFile(f, repoXmlFile);
        } else if (repoXmlURL != null) {
            final URL url;
            try {
                url = new URL(repoXmlURL);
            } catch (MalformedURLException e) {
                throw new RuntimeException("The OBR repository resolver " + getName()
                        + " couldn't be configured: repoXmlURL '" + repoXmlURL + "' is not an URL");
            }

            // compute the base URL
            URL baseUrl;
            String basePath = "/";
            int i = url.getPath().lastIndexOf("/");
            if (i > 0) {
                basePath = url.getPath().substring(0, i + 1);
            }
            try {
                baseUrl = new URL(url.getProtocol(), url.getHost(), url.getPort(), basePath);
            } catch (MalformedURLException e) {
                throw new RuntimeException(
                        "The OBR repository resolver "
                                + getName()
                                + " couldn't be configured: the base url couldn'd be extracted from the url "
                                + url + " (" + e.getMessage() + ")");
            }
            RelativeURLRepository repo = new RelativeURLRepository(baseUrl);
            setRepository(repo);

            ArtifactDownloadReport report;
            EventManager eventManager = getEventManager();
            try {
                if (eventManager != null) {
                    getRepository().addTransferListener(eventManager);
                }
                Resource obrResource = new URLResource(url);
                CacheResourceOptions options = new CacheResourceOptions();
                report = getRepositoryCacheManager().downloadRepositoryResource(obrResource, "obr",
                    "obr", "xml", options, repo);
            } finally {
                if (eventManager != null) {
                    getRepository().removeTransferListener(eventManager);
                }
            }

            loadRepoFromFile(report.getLocalFile(), repoXmlURL);

        } else {
            throw new RuntimeException("The OBR repository resolver " + getName()
                    + " couldn't be configured: repoXmlFile or repoXmlUrl is missing");
        }
    }

    private void loadRepoFromFile(File repoFile, String sourceLocation) {
        FileInputStream in;
        try {
            in = new FileInputStream(repoFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("The OBR repository resolver " + getName()
                    + " couldn't be configured: the file " + sourceLocation + " was not found");
        }
        try {
            setRepoDescriptor(OBRXMLParser.parse(in));
        } catch (ParseException e) {
            throw new RuntimeException("The OBR repository resolver " + getName()
                    + " couldn't be configured: the file " + sourceLocation
                    + " is incorrectly formed (" + e.getMessage() + ")");
        } catch (IOException e) {
            throw new RuntimeException("The OBR repository resolver " + getName()
                    + " couldn't be configured: the file " + sourceLocation
                    + " could not be read (" + e.getMessage() + ")");
        } catch (SAXException e) {
            throw new RuntimeException("The OBR repository resolver " + getName()
                    + " couldn't be configured: the file " + sourceLocation
                    + " has incorrect XML (" + e.getMessage() + ")");
        }
        try {
            in.close();
        } catch (IOException e) {
            // don't care
        }
    }
}
