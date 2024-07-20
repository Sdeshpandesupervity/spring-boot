/*
 * Copyright 2012-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.devtools.autoconfigure;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.devtools.autoconfigure.DevToolsProperties.Restart;
import org.springframework.boot.devtools.classpath.ClassPathChangedEvent;
import org.springframework.boot.devtools.classpath.ClassPathFileSystemWatcher;
import org.springframework.boot.devtools.classpath.ClassPathRestartStrategy;
import org.springframework.boot.devtools.classpath.PatternClassPathRestartStrategy;
import org.springframework.boot.devtools.filewatch.FileSystemWatcher;
import org.springframework.boot.devtools.filewatch.FileSystemWatcherFactory;
import org.springframework.boot.devtools.filewatch.SnapshotStateRepository;
import org.springframework.boot.devtools.livereload.LiveReloadServer;
import org.springframework.boot.devtools.restart.ConditionalOnInitializedRestarter;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.devtools.restart.Restarter;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.ResolvableType;
import org.springframework.core.log.LogMessage;
import org.springframework.util.StringUtils;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for local development support.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Vladimir Tsanev
 * @author Akshay Dubey
 * @since 1.3.0
 */
@AutoConfiguration
@ConditionalOnInitializedRestarter
@EnableConfigurationProperties(DevToolsProperties.class)
public class LocalDevToolsAutoConfiguration {

	/**
	 * Local LiveReload configuration.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = "spring.devtools.livereload", name = "enabled", matchIfMissing = true)
	static class LiveReloadConfiguration {

		@Bean
		@RestartScope
		@ConditionalOnMissingBean
		LiveReloadServer liveReloadServer(DevToolsProperties properties) {
			return new LiveReloadServer(properties.getLivereload().getPort(),
					Restarter.getInstance().getThreadFactory());
		}

		@Bean
		OptionalLiveReloadServer optionalLiveReloadServer(LiveReloadServer liveReloadServer) {
			return new OptionalLiveReloadServer(liveReloadServer);
		}

		@Bean
		LiveReloadServerEventListener liveReloadServerEventListener(OptionalLiveReloadServer liveReloadServer) {
			return new LiveReloadServerEventListener(liveReloadServer);
		}

		@Bean
		LiveReloadForAdditionalPaths liveReloadForAdditionalPaths(LiveReloadServer liveReloadServer,
				DevToolsProperties properties, FileSystemWatcher fileSystemWatcher) {
			return new LiveReloadForAdditionalPaths(liveReloadServer, properties.getLivereload().getAdditionalPaths(),
					fileSystemWatcher);
		}

		@Bean
		FileSystemWatcher fileSystemWatcher(DevToolsProperties properties) {
			return new FileSystemWatcher(true, properties.getLivereload().getPollInterval(),
					properties.getLivereload().getQuietPeriod());
		}

	}

	/**
	 * Local Restart Configuration.
	 */
	@Lazy(false)
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = "spring.devtools.restart", name = "enabled", matchIfMissing = true)
	static class RestartConfiguration {

		private final DevToolsProperties properties;

		RestartConfiguration(DevToolsProperties properties) {
			this.properties = properties;
		}

		@Bean
		RestartingClassPathChangeChangedEventListener restartingClassPathChangedEventListener(
				FileSystemWatcherFactory fileSystemWatcherFactory) {
			return new RestartingClassPathChangeChangedEventListener(fileSystemWatcherFactory);
		}

		@Bean
		@ConditionalOnMissingBean
		ClassPathFileSystemWatcher classPathFileSystemWatcher(FileSystemWatcherFactory fileSystemWatcherFactory,
				ClassPathRestartStrategy classPathRestartStrategy) {
			URL[] urls = Restarter.getInstance().getInitialUrls();
			ClassPathFileSystemWatcher watcher = new ClassPathFileSystemWatcher(fileSystemWatcherFactory,
					classPathRestartStrategy, urls);
			watcher.setStopWatcherOnRestart(true);
			return watcher;
		}

		@Bean
		@ConditionalOnMissingBean
		ClassPathRestartStrategy classPathRestartStrategy() {
			return new PatternClassPathRestartStrategy(this.properties.getRestart().getAllExclude());
		}

		@Bean
		FileSystemWatcherFactory fileSystemWatcherFactory() {
			return this::newFileSystemWatcher;
		}

		@Bean
		@ConditionalOnProperty(prefix = "spring.devtools.restart", name = "log-condition-evaluation-delta",
				matchIfMissing = true)
		ConditionEvaluationDeltaLoggingListener conditionEvaluationDeltaLoggingListener() {
			return new ConditionEvaluationDeltaLoggingListener();
		}

		private FileSystemWatcher newFileSystemWatcher() {
			Restart restartProperties = this.properties.getRestart();
			FileSystemWatcher watcher = new FileSystemWatcher(true, restartProperties.getPollInterval(),
					restartProperties.getQuietPeriod(), SnapshotStateRepository.STATIC);
			String triggerFile = restartProperties.getTriggerFile();
			if (StringUtils.hasLength(triggerFile)) {
				watcher.setTriggerFilter(new TriggerFileFilter(triggerFile));
			}
			List<File> additionalPaths = restartProperties.getAdditionalPaths();
			for (File path : additionalPaths) {
				watcher.addSourceDirectory(path.getAbsoluteFile());
			}
			return watcher;
		}

	}

	static class LiveReloadServerEventListener implements GenericApplicationListener {

		private final OptionalLiveReloadServer liveReloadServer;

		LiveReloadServerEventListener(OptionalLiveReloadServer liveReloadServer) {
			this.liveReloadServer = liveReloadServer;
		}

		@Override
		public boolean supportsEventType(ResolvableType eventType) {
			Class<?> type = eventType.getRawClass();
			if (type == null) {
				return false;
			}
			return ContextRefreshedEvent.class.isAssignableFrom(type)
					|| ClassPathChangedEvent.class.isAssignableFrom(type);
		}

		@Override
		public boolean supportsSourceType(Class<?> sourceType) {
			return true;
		}

		@Override
		public void onApplicationEvent(ApplicationEvent event) {
			if (event instanceof ContextRefreshedEvent || (event instanceof ClassPathChangedEvent classPathChangedEvent
					&& !classPathChangedEvent.isRestartRequired())) {
				this.liveReloadServer.triggerReload();
			}
		}

		@Override
		public int getOrder() {
			return 0;
		}

	}

	static class RestartingClassPathChangeChangedEventListener implements ApplicationListener<ClassPathChangedEvent> {

		private static final Log logger = LogFactory.getLog(RestartingClassPathChangeChangedEventListener.class);

		private final FileSystemWatcherFactory fileSystemWatcherFactory;

		RestartingClassPathChangeChangedEventListener(FileSystemWatcherFactory fileSystemWatcherFactory) {
			this.fileSystemWatcherFactory = fileSystemWatcherFactory;
		}

		@Override
		public void onApplicationEvent(ClassPathChangedEvent event) {
			if (event.isRestartRequired()) {
				logger.info(LogMessage.format("Restarting due to %s", event.overview()));
				logger.debug(LogMessage.format("Change set: %s", event.getChangeSet()));
				Restarter.getInstance().restart(new FileWatchingFailureHandler(this.fileSystemWatcherFactory));
			}
		}

	}

	static class LiveReloadForAdditionalPaths implements DisposableBean {

		private final FileSystemWatcher fileSystemWatcher;

		@Override
		public void destroy() throws Exception {
			if (this.fileSystemWatcher != null) {
				this.fileSystemWatcher.stop();
			}
		}

		LiveReloadForAdditionalPaths(LiveReloadServer liveReloadServer, List<File> staticLocations,
				FileSystemWatcher fileSystemWatcher) {
			this.fileSystemWatcher = fileSystemWatcher;

			for (File path : staticLocations) {
				this.fileSystemWatcher.addSourceDirectory(path.getAbsoluteFile());
			}

			this.fileSystemWatcher.addListener((__) -> liveReloadServer.triggerReload());

			this.fileSystemWatcher.start();
		}

	}

}
