// Copyright (c) 2021-present, Kinescope
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import 'dart:async';

import 'package:flutter_kinescope_sdk_example/theme/demo_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_kinescope_sdk/flutter_kinescope_sdk.dart';

class OfflineAddDownloadPage extends StatefulWidget {
  const OfflineAddDownloadPage({Key? key}) : super(key: key);

  @override
  State<OfflineAddDownloadPage> createState() => _OfflineAddDownloadPageState();
}

class _OfflineAddDownloadPageState extends State<OfflineAddDownloadPage> {
  final _downloads = KinescopeOfflineDownload.instance;
  StreamSubscription<KinescopeDownloadUpdate>? _subscription;
  Timer? _progressTimer;

  List<KinescopeCatalogVideo> _catalog = const [];
  Map<String, KinescopeDownloadInfo> _downloadsByVideoId = {};
  final Set<String> _startingDownloads = {};
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    await _downloads.initialize();
    _subscription = _downloads.updates.listen(_onDownloadUpdate);
    _startProgressPolling();
    await _reload();
  }

  void _startProgressPolling() {
    _progressTimer?.cancel();
    _progressTimer = Timer.periodic(const Duration(milliseconds: 500), (_) {
      _refreshDownloadProgress();
    });
  }

  Future<void> _refreshDownloadProgress() async {
    final hasActive = _startingDownloads.isNotEmpty ||
        _downloadsByVideoId.values.any((item) => item.isDownloading);
    if (!hasActive || !mounted) {
      return;
    }

    final items = await _downloads.getAllDownloads();
    if (!mounted) {
      return;
    }

    final next = _indexDownloadsByVideoId(items);
    setState(() {
      for (final entry in next.entries) {
        _downloadsByVideoId[entry.key] = entry.value;
      }
      _startingDownloads.removeWhere(
        (videoId) =>
            next.containsKey(videoId) &&
            (next[videoId]!.isDownloading || next[videoId]!.isCompleted),
      );
    });
  }

  Future<void> _reload() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final results = await Future.wait([
        _downloads.getVideoCatalog(),
        _downloads.getAllDownloads(),
      ]);
      if (!mounted) {
        return;
      }
      setState(() {
        _catalog = results[0] as List<KinescopeCatalogVideo>;
        _downloadsByVideoId = _indexDownloadsByVideoId(
          results[1] as List<KinescopeDownloadInfo>,
        );
        _loading = false;
      });
    } on Object catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = error.toString();
        _loading = false;
      });
    }
  }

  Map<String, KinescopeDownloadInfo> _indexDownloadsByVideoId(
    List<KinescopeDownloadInfo> items,
  ) {
    final map = <String, KinescopeDownloadInfo>{};
    for (final item in items) {
      final videoId = item.videoId;
      if (videoId != null && videoId.isNotEmpty) {
        map[videoId] = item;
      }
    }
    return map;
  }

  void _onDownloadUpdate(KinescopeDownloadUpdate update) {
    final videoId = update.download.videoId;
    setState(() {
      if (videoId != null && videoId.isNotEmpty) {
        if (update.download.isCompleted) {
          _downloadsByVideoId[videoId] = update.download;
          _startingDownloads.remove(videoId);
        } else if (update.download.isFailed) {
          _startingDownloads.remove(videoId);
          _downloadsByVideoId[videoId] = update.download;
        } else {
          _downloadsByVideoId[videoId] = update.download;
        }
      }
    });

    if (update.error != null && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Download error: ${update.error}')),
      );
    }

    if (update.download.isCompleted && mounted) {
      final title = update.download.title ?? 'Video';
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('"$title" downloaded')),
      );
      Navigator.of(context).pop(true);
    }
  }

  @override
  void dispose() {
    _progressTimer?.cancel();
    _subscription?.cancel();
    super.dispose();
  }

  Future<void> _startDownload(KinescopeCatalogVideo video) async {
    final existing = _downloadsByVideoId[video.id];
    if (existing?.isCompleted ?? false) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Video already downloaded')),
      );
      return;
    }

    setState(() => _startingDownloads.add(video.id));

    try {
      final info = await _downloads.downloadVideo(video.id);
      if (!mounted) {
        return;
      }
      setState(() {
        _startingDownloads.remove(video.id);
        _downloadsByVideoId[info.videoId ?? video.id] = info;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Downloading "${video.title}"...')),
      );
    } on Object catch (error) {
      if (!mounted) {
        return;
      }
      setState(() => _startingDownloads.remove(video.id));
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to start download: $error')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: DemoTheme.demoWhite,
      appBar: AppBar(
        title: const Text('Download video'),
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                _error!,
                textAlign: TextAlign.center,
                style: const TextStyle(color: DemoTheme.emptyText),
              ),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: _reload,
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }

    if (_catalog.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text(
            'No videos available for download.',
            textAlign: TextAlign.center,
            style: TextStyle(
              color: DemoTheme.emptyText,
              fontSize: 16,
            ),
          ),
        ),
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.symmetric(vertical: 8),
      itemCount: _catalog.length,
      separatorBuilder: (_, __) => const Divider(height: 1),
      itemBuilder: (context, index) {
        final video = _catalog[index];
        final download = _downloadsByVideoId[video.id];
        final isStarting = _startingDownloads.contains(video.id);
        return _CatalogDownloadItem(
          video: video,
          download: download,
          isStarting: isStarting,
          onDownload: () => _startDownload(video),
        );
      },
    );
  }
}

class _CatalogDownloadItem extends StatelessWidget {
  const _CatalogDownloadItem({
    required this.video,
    required this.onDownload,
    required this.isStarting,
    this.download,
  });

  final KinescopeCatalogVideo video;
  final KinescopeDownloadInfo? download;
  final bool isStarting;
  final VoidCallback onDownload;

  @override
  Widget build(BuildContext context) {
    final isDownloading = isStarting || (download?.isDownloading ?? false);
    final isCompleted = download?.isCompleted ?? false;
    final isFailed = download?.isFailed ?? false;
    final percent = download?.percent ?? 0;
    final hasProgress = percent > 0;
    final durationLabel = video.durationLabel;

    return Material(
      color: DemoTheme.demoWhite,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    video.title,
                    style: const TextStyle(
                      color: DemoTheme.playlistTextPrimary,
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                  if (durationLabel.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(
                      durationLabel,
                      style: const TextStyle(
                        color: DemoTheme.emptyText,
                        fontSize: 12,
                      ),
                    ),
                  ],
                  if (isDownloading) ...[
                    const SizedBox(height: 12),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: LinearProgressIndicator(
                        value: hasProgress ? percent / 100 : null,
                        minHeight: 4,
                        color: DemoTheme.kinescopePrimary,
                        backgroundColor: const Color(0xFFE8E8E8),
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _downloadStatusLabel(
                        isStarting: isStarting,
                        download: download,
                      ),
                      style: const TextStyle(
                        fontSize: 12,
                        color: DemoTheme.emptyText,
                      ),
                    ),
                  ] else if (isFailed) ...[
                    const SizedBox(height: 8),
                    const Text(
                      'Download failed',
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.red,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(width: 8),
            if (isDownloading)
              const SizedBox(
                width: 48,
                height: 48,
              )
            else if (isCompleted)
              const Padding(
                padding: EdgeInsets.only(top: 8),
                child: Text(
                  'Downloaded',
                  style: TextStyle(
                    color: DemoTheme.emptyText,
                    fontSize: 13,
                  ),
                ),
              )
            else
              IconButton(
                onPressed: onDownload,
                icon: Icon(isFailed ? Icons.refresh : Icons.add),
                color: DemoTheme.kinescopePrimary,
                tooltip: isFailed ? 'Retry' : 'Download',
              ),
          ],
        ),
      ),
    );
  }

  String _downloadStatusLabel({
    required bool isStarting,
    required KinescopeDownloadInfo? download,
  }) {
    if (isStarting && download == null) {
      return 'Preparing download...';
    }
    if (download != null) {
      return download.progressLabel;
    }
    return 'Downloading...';
  }
}
