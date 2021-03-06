/**
 * Copyright (c) 2017, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.afs.local.storage;

import com.google.common.collect.ImmutableList;
import com.powsybl.afs.Folder;
import com.powsybl.afs.storage.AppFileSystemStorage;
import com.powsybl.afs.storage.NodeId;
import com.powsybl.commons.datasource.DataSource;
import com.powsybl.computation.ComputationManager;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LocalAppFileSystemStorage implements AppFileSystemStorage {

    private final Path rootDir;

    private final String fileSystemName;

    private final List<LocalFileScanner> fileScanners;

    private final List<LocalFolderScanner> folderScanners;

    private final ComputationManager computationManager;

    private final Map<Path, LocalFile> fileCache = new HashMap<>();

    private final Map<Path, LocalFolder> folderCache = new HashMap<>();

    public LocalAppFileSystemStorage(Path rootDir, String fileSystemName, List<LocalFileScanner> fileScanners,
                                     List<LocalFolderScanner> folderScanners, ComputationManager computationManager) {
        this.rootDir = Objects.requireNonNull(rootDir);
        this.fileSystemName = Objects.requireNonNull(fileSystemName);
        this.fileScanners = Objects.requireNonNull(fileScanners);
        this.folderScanners = ImmutableList.<LocalFolderScanner>builder()
                .addAll(Objects.requireNonNull(folderScanners))
                .add(new DefaultLocalFolderScanner())
                .build();
        this.computationManager = Objects.requireNonNull(computationManager);
    }

    private LocalFile scanFile(Path path, boolean useCache) {
        LocalFile file = null;
        if (useCache && fileCache.containsKey(path)) {
            file = fileCache.get(path);
        } else {
            LocalFileScannerContext context = new LocalFileScannerContext(computationManager);
            for (LocalFileScanner fileScanner : fileScanners) {
                file = fileScanner.scanFile(path, context);
                if (file != null) {
                    break;
                }
            }
            fileCache.put(path, file);
        }
        return file;
    }

    private LocalFolder scanFolder(Path path, boolean useCache) {
        LocalFolder folder = null;
        if (useCache && folderCache.containsKey(path)) {
            folder = folderCache.get(path);
        } else {
            LocalFolderScannerContext context = new LocalFolderScannerContext(rootDir, fileSystemName, computationManager);
            for (LocalFolderScanner folderScanner : folderScanners) {
                folder = folderScanner.scanFolder(path, context);
                if (folder != null) {
                    break;
                }
            }
            folderCache.put(path, folder);
        }
        return folder;
    }

    @Override
    public NodeId fromString(String str) {
        return new PathNodeId(rootDir.getFileSystem().getPath(str));
    }

    @Override
    public NodeId getRootNode() {
        return new PathNodeId(rootDir);
    }

    @Override
    public String getNodePseudoClass(NodeId nodeId) {
        Objects.requireNonNull(nodeId);
        Path path = ((PathNodeId) nodeId).getPath();
        LocalFile file = scanFile(path, true);
        if (file != null) {
            return file.getPseudoClass();
        } else {
            LocalFolder folder = scanFolder(path, true);
            if (folder != null) {
                return Folder.PSEUDO_CLASS;
            } else {
                throw new AssertionError();
            }
        }
    }

    @Override
    public String getNodeName(NodeId nodeId) {
        Objects.requireNonNull(nodeId);
        Path path = ((PathNodeId) nodeId).getPath();
        LocalFile file = scanFile(path, true);
        if (file != null) {
            return file.getName();
        } else {
            LocalFolder folder = scanFolder(path, true);
            if (folder != null) {
                return folder.getName();
            } else {
                throw new AssertionError();
            }
        }
    }

    private boolean isLocalNode(Path path) {
        return scanFolder(path, false) != null || scanFile(path, false) != null;
    }

    @Override
    public List<NodeId> getChildNodes(NodeId nodeId) {
        Objects.requireNonNull(nodeId);
        Path path = ((PathNodeId) nodeId).getPath();
        List<NodeId> childNodesIds = new ArrayList<>();
        LocalFolder folder = scanFolder(path, false);
        if (folder != null) {
            childNodesIds.addAll(folder.getChildPaths().stream()
                    .filter(childPath -> isLocalNode(childPath))
                    .map(childPath -> new PathNodeId(childPath))
                    .collect(Collectors.toList()));
        } else {
            throw new AssertionError();
        }
        return childNodesIds;
    }

    @Override
    public NodeId getChildNode(NodeId nodeId, String name) {
        Objects.requireNonNull(nodeId);
        Objects.requireNonNull(name);
        Path path = ((PathNodeId) nodeId).getPath();
        LocalFolder folder = scanFolder(path, false);
        if (folder != null) {
            Path childPath = folder.getChildPath(name);
            if (childPath != null && isLocalNode(childPath)) {
                return new PathNodeId(childPath);
            }
        }
        return null;
    }

    @Override
    public NodeId getParentNode(NodeId nodeId) {
        Objects.requireNonNull(nodeId);
        Path path = ((PathNodeId) nodeId).getPath();
        Path parentPath;
        LocalFile file = scanFile(path, true);
        if (file != null) {
            parentPath = file.getParentPath();
        } else {
            LocalFolder folder = scanFolder(path, true);
            if (folder != null) {
                parentPath = folder.getParentPath();
            } else {
                throw new AssertionError();
            }
        }
        return parentPath == null ? null : new PathNodeId(parentPath);
    }

    @Override
    public void setParentNode(NodeId nodeId, NodeId newParentNodeId) {
        throw new AssertionError();
    }

    @Override
    public boolean isWritable(NodeId nodeId) {
        return false;
    }

    @Override
    public NodeId createNode(NodeId parentNodeId, String name, String nodePseudoClass) {
        throw new AssertionError();
    }

    @Override
    public void deleteNode(NodeId nodeId) {
        throw new AssertionError();
    }

    @Override
    public String getStringAttribute(NodeId nodeId, String name) {
        Objects.requireNonNull(nodeId);
        Path path = ((PathNodeId) nodeId).getPath();
        LocalFile file = scanFile(path, true);
        if (file != null) {
            return file.getStringAttribute(name);
        }
        throw new AssertionError();
    }

    @Override
    public void setStringAttribute(NodeId nodeId, String name, String value) {
        throw new AssertionError();
    }

    @Override
    public Reader readStringAttribute(NodeId nodeId, String name) {
        throw new AssertionError();
    }

    @Override
    public Writer writeStringAttribute(NodeId nodeId, String name) {
        throw new AssertionError();
    }

    @Override
    public OptionalInt getIntAttribute(NodeId nodeId, String name) {
        throw new AssertionError();
    }

    @Override
    public void setIntAttribute(NodeId nodeId, String name, int value) {
        throw new AssertionError();
    }

    @Override
    public OptionalDouble getDoubleAttribute(NodeId nodeId, String name) {
        throw new AssertionError();
    }

    @Override
    public void setDoubleAttribute(NodeId nodeId, String name, double value) {
        throw new AssertionError();
    }

    @Override
    public Optional<Boolean> getBooleanAttribute(NodeId nodeId, String name) {
        throw new AssertionError();
    }

    @Override
    public void setBooleanAttribute(NodeId nodeId, String name, boolean value) {
        throw new AssertionError();
    }

    @Override
    public DataSource getDataSourceAttribute(NodeId nodeId, String name) {
        Objects.requireNonNull(nodeId);
        Path path = ((PathNodeId) nodeId).getPath();
        LocalFile file = scanFile(path, true);
        if (file != null) {
            return file.getDataSourceAttribute(name);
        }
        throw new AssertionError();
    }

    @Override
    public NodeId getDependency(NodeId nodeId, String name) {
        throw new AssertionError();
    }

    @Override
    public void addDependency(NodeId nodeId, String name, NodeId toNodeId) {
        throw new AssertionError();
    }

    @Override
    public List<NodeId> getDependencies(NodeId nodeId) {
        throw new AssertionError();
    }

    @Override
    public List<NodeId> getBackwardDependencies(NodeId nodeId) {
        throw new AssertionError();
    }

    @Override
    public NodeId getProjectRootNode(NodeId projectId) {
        throw new AssertionError();
    }

    @Override
    public InputStream readFromCache(NodeId projectFileId, String key) {
        throw new AssertionError();
    }

    @Override
    public OutputStream writeToCache(NodeId projectFileId, String key) {
        throw new AssertionError();
    }

    @Override
    public void invalidateCache(NodeId projectFileId, String key) {
        throw new AssertionError();
    }

    @Override
    public void invalidateCache() {
        throw new AssertionError();
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}
