/**
 * Copyright (c) 2017, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.afs;

import com.powsybl.afs.storage.AppFileSystemStorage;
import com.powsybl.afs.storage.NodeId;
import com.powsybl.afs.storage.PseudoClass;

import java.util.ResourceBundle;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class Project extends File {

    public static final String PSEUDO_CLASS = PseudoClass.PROJECT_PSEUDO_CLASS;

    private static final String PROJECT_LABEL = ResourceBundle.getBundle("lang.Project").getString("Project");

    private static final FileIcon PROJECT_ICON = new FileIcon(PROJECT_LABEL, Project.class.getResourceAsStream("/icons/project16x16.png"));

    public Project(NodeId id, AppFileSystemStorage storage, AppFileSystem fileSystem) {
        super(id, storage, fileSystem, PROJECT_ICON);
    }

    public ProjectFolder getRootFolder() {
        return new ProjectFolder(storage.getProjectRootNode(id), storage, id, fileSystem);
    }
}
