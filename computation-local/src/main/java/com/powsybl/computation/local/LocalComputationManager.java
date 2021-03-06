/**
 * Copyright (c) 2016, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.local;

import com.google.common.io.ByteStreams;
import com.powsybl.commons.io.WorkingDirectory;
import com.powsybl.computation.*;
import net.java.truevfs.comp.zip.ZipEntry;
import net.java.truevfs.comp.zip.ZipFile;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LocalComputationManager implements ComputationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalComputationManager.class);

    private final LocalComputationConfig config;

    private final WorkingDirectory commonDir;

    private final LocalComputationResourcesStatus status;

    private final Semaphore permits;

    private final LocalExecutor localExecutor;

    private static final Lock LOCK = new ReentrantLock();

    private static LocalComputationManager DEFAULT;

    public static ComputationManager getDefault() {
        LOCK.lock();
        try {
            if (DEFAULT == null) {
                try {
                    DEFAULT = new LocalComputationManager();
                    Runtime.getRuntime().addShutdownHook(new Thread() {
                        @Override
                        public void run() {
                            try {
                                DEFAULT.close();
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                    });
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return DEFAULT;
        } finally {
            LOCK.unlock();
        }
    }

    private static LocalExecutor getLocalExecutor() {
        if (SystemUtils.IS_OS_WINDOWS) {
            return new WindowsLocalExecutor();
        } else if (SystemUtils.IS_OS_UNIX) {
            return new UnixLocalExecutor();
        } else {
            throw new UnsupportedOperationException("OS not supported for local execution");
        }
    }

    public LocalComputationManager() throws IOException {
        this(LocalComputationConfig.load());
    }

    public LocalComputationManager(Path localDir)  throws IOException {
        this(new LocalComputationConfig(localDir));
    }

    public LocalComputationManager(LocalComputationConfig config) throws IOException {
        this(config, getLocalExecutor());
    }

    public LocalComputationManager(LocalComputationConfig config, LocalExecutor localExecutor) throws IOException {
        this.config = Objects.requireNonNull(config);
        this.localExecutor = Objects.requireNonNull(localExecutor);
        status = new LocalComputationResourcesStatus(config.getAvailableCore());
        permits = new Semaphore(config.getAvailableCore());
        //make sure the localdir exists
        Files.createDirectories(config.getLocalDir());
        commonDir = new WorkingDirectory(config.getLocalDir(), "itools_common_", false);
        LOGGER.info(config.toString());
    }

    @Override
    public String getVersion() {
        return "none (local mode)";
    }

    @Override
    public Path getLocalDir() {
        return config.getLocalDir();
    }

    @Override
    public OutputStream newCommonFile(String fileName) throws IOException {
        return Files.newOutputStream(commonDir.toPath().resolve(fileName));
    }

    @Override
    public CommandExecutor newCommandExecutor(Map<String, String> env, String workingDirPrefix, boolean debug) throws Exception {
        Objects.requireNonNull(env);
        Objects.requireNonNull(workingDirPrefix);

        return new CommandExecutor() {
            private final WorkingDirectory workingDir = new WorkingDirectory(config.getLocalDir(), workingDirPrefix, debug);

            @Override
            public Path getWorkingDir() {
                return workingDir.toPath();
            }

            @Override
            public void start(CommandExecution execution, ExecutionListener listener) throws Exception {
                enter();
                try {
                    if (listener != null) {
                        listener.onExecutionStart(0, execution.getExecutionCount() - 1);
                    }
                    ExecutionReport report = execute(workingDir.toPath(), Collections.singletonList(execution), env, (execution1, executionIndex) -> {
                        if (listener != null) {
                            listener.onExecutionCompletion(executionIndex);
                        }
                    });
                    if (listener != null) {
                        listener.onEnd(report);
                    }
                } finally {
                    exit();
                }
            }

            @Override
            public ExecutionReport start(CommandExecution execution) throws Exception {
                enter();
                try {
                    return execute(workingDir.toPath(), Collections.singletonList(execution), env, null);
                } finally {
                    exit();
                }
            }

            @Override
            public void close() throws Exception {
                workingDir.close();
            }
        };
    }

    private interface ExecutionMonitor {

        void onProgress(CommandExecution execution, int executionIndex);

    }

    private ExecutionReport execute(Path workingDir, List<CommandExecution> commandExecutionList, Map<String, String> variables, ExecutionMonitor monitor)
            throws IOException, InterruptedException {
        List<ExecutionError> errors = new ArrayList<>();

        for (CommandExecution commandExecution : commandExecutionList) {
            Command command = commandExecution.getCommand();

            for (int executionIndex = 0; executionIndex < commandExecution.getExecutionCount(); executionIndex++) {
                LOGGER.debug("Executing command {} in working directory {}",
                        command.toString(executionIndex), workingDir);

                // pre-processing
                for (InputFile file : command.getInputFiles()) {
                    String fileName = file.getName(executionIndex);

                    // first check if the file exists in the working directory
                    Path path = workingDir.resolve(fileName);
                    if (!Files.exists(path)) {
                        // if not check if the file exists in the common directory
                        path = commonDir.toPath().resolve(fileName);
                        if (!Files.exists(path)) {
                            throw new RuntimeException("Input file '" + fileName + "' not found in the working and common directory");
                        }
                        if (file.getPreProcessor() == null) {
                            Files.copy(path, workingDir.resolve(path.getFileName()));
                        }
                    }
                    if (file.getPreProcessor() != null) {
                        switch (file.getPreProcessor()) {
                            case FILE_GUNZIP:
                                // gunzip the file
                                try (InputStream is = new GZIPInputStream(Files.newInputStream(path));
                                     OutputStream os = Files.newOutputStream(workingDir.resolve(fileName.substring(0, fileName.length() - 3)))) {
                                    ByteStreams.copy(is, os);
                                }
                                break;
                            case ARCHIVE_UNZIP:
                                // extract the archive
                                try (ZipFile zipFile = new ZipFile(path)) {
                                    for (ZipEntry ze : Collections.list(zipFile.entries())) {
                                        Files.copy(zipFile.getInputStream(ze.getName()), workingDir.resolve(ze.getName()), REPLACE_EXISTING);
                                    }
                                }
                                break;

                            default:
                                throw new InternalError();
                        }
                    }
                }

                int exitValue = 0;
                Path outFile = workingDir.resolve(command.getId() + "_" + executionIndex + ".out");
                Path errFile = workingDir.resolve(command.getId() + "_" + executionIndex + ".err");
                Map<String, String> executionVariables = CommandExecution.getExecutionVariables(variables, commandExecution);
                switch (command.getType()) {
                    case SIMPLE:
                        SimpleCommand simpleCmd = (SimpleCommand) command;
                        exitValue = localExecutor.execute(simpleCmd.getProgram(),
                                simpleCmd.getArgs(executionIndex),
                                outFile,
                                errFile,
                                workingDir,
                                executionVariables);
                        break;
                    case GROUP:
                        for (GroupCommand.SubCommand subCmd : ((GroupCommand) command).getSubCommands()) {
                            exitValue = localExecutor.execute(subCmd.getProgram(),
                                    subCmd.getArgs(executionIndex),
                                    outFile,
                                    errFile,
                                    workingDir,
                                    executionVariables);
                            if (exitValue != 0) {
                                break;
                            }
                        }
                        break;
                    default:
                        throw new InternalError();
                }

                if (exitValue != 0) {
                    errors.add(new ExecutionError(command, executionIndex, exitValue));
                } else {
                    // post processing
                    for (OutputFile file : command.getOutputFiles()) {
                        String fileName = file.getName(executionIndex);
                        Path path = workingDir.resolve(fileName);
                        if (file.getPostProcessor() != null && Files.isRegularFile(path)) {
                            switch (file.getPostProcessor()) {
                                case FILE_GZIP:
                                    // gzip the file
                                    try (InputStream is = Files.newInputStream(path);
                                         OutputStream os = new GZIPOutputStream(Files.newOutputStream(workingDir.resolve(fileName + ".gz")))) {
                                        ByteStreams.copy(is, os);
                                    }
                                    break;

                                default:
                                    throw new InternalError();
                            }
                        }
                    }
                }

                if (monitor != null) {
                    monitor.onProgress(commandExecution, executionIndex);
                }
            }
        }
        return new ExecutionReport(errors);
    }

    private void enter() throws InterruptedException {
        permits.acquire();
        status.incrementNumberOfBusyCores();
    }

    private void exit() {
        status.decrementNumberOfBusyCores();
        permits.release();
    }

    @Override
    public <R> CompletableFuture<R> execute(ExecutionEnvironment environment, ExecutionHandler<R> handler) {
        Objects.requireNonNull(environment);
        Objects.requireNonNull(handler);
        CompletableFuture<R> f = new CompletableFuture<>();
        getExecutor().execute(() -> {
            try {
                try (WorkingDirectory workingDir = new WorkingDirectory(config.getLocalDir(), environment.getWorkingDirPrefix(), environment.isDebug())) {
                    List<CommandExecution> commandExecutionList = handler.before(workingDir.toPath());
                    ExecutionReport report = null;
                    enter();
                    try {
                        report = execute(workingDir.toPath(), commandExecutionList, environment.getVariables(), (execution, executionIndex) -> handler.onProgress(execution, executionIndex));
                    } finally {
                        exit();
                    }
                    R result = handler.after(workingDir.toPath(), report);
                    f.complete(result);
                }
            } catch (Exception e) {
                f.completeExceptionally(e);
            }
        });
        return f;
    }

    @Override
    public ComputationResourcesStatus getResourcesStatus() {
        return status;
    }

    @Override
    public Executor getExecutor() {
        return ForkJoinPool.commonPool();
    }

    @Override
    public void close() throws IOException {
        commonDir.close();
    }

}
