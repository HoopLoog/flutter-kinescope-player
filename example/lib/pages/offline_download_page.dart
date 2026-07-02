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

import 'package:flutter_kinescope_sdk_example/pages/offline_add_download_page.dart';
import 'package:flutter_kinescope_sdk_example/pages/offline_player_page.dart';
import 'package:flutter_kinescope_sdk_example/theme/demo_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_kinescope_sdk/flutter_kinescope_sdk.dart';

class OfflineDownloadPage extends StatefulWidget {
  const OfflineDownloadPage({Key? key}) : super(key: key);

  @override
  State<OfflineDownloadPage> createState() => _OfflineDownloadPageState();
}

class _OfflineDownloadPageState extends State<OfflineDownloadPage> {
  final _downloads = KinescopeOfflineDownload.instance;
  StreamSubscription<KinescopeDownloadUpdate>? _subscription;

  List<KinescopeDownloadInfo> _items = const [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    await _downloads.initialize();
    _subscription = _downloads.updates.listen((_) => _reload());
    await _reload();
  }

  Future<void> _reload() async {
    final items = await _downloads.getCompletedDownloads();
    if (!mounted) {
      return;
    }
    setState(() {
      _items = items;
      _loading = false;
    });
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }

  Future<void> _openAddDownload() async {
    final added = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) => const OfflineAddDownloadPage(),
      ),
    );
    if (added == true) {
      await _reload();
    }
  }

  Future<void> _deleteDownload(KinescopeDownloadInfo item) async {
    await _downloads.removeDownload(item.contentId);
    if (!mounted) {
      return;
    }
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Video removed')),
    );
    await _reload();
  }

  void _playOffline(KinescopeDownloadInfo item) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => OfflinePlayerPage(
          contentId: item.contentId,
          title: item.title,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: DemoTheme.demoWhite,
      appBar: AppBar(
        title: const Text('Offline viewing'),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _openAddDownload,
        backgroundColor: DemoTheme.kinescopePrimary,
        foregroundColor: DemoTheme.demoWhite,
        child: const Icon(Icons.add),
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_items.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text(
            'No downloaded videos.\nTap + to download from your project.',
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
      itemCount: _items.length,
      separatorBuilder: (_, __) => const Divider(height: 1),
      itemBuilder: (context, index) {
        final item = _items[index];
        return _OfflineListItem(
          item: item,
          onTap: () => _playOffline(item),
          onDelete: () => _deleteDownload(item),
        );
      },
    );
  }
}

class _OfflineListItem extends StatelessWidget {
  const _OfflineListItem({
    required this.item,
    required this.onTap,
    required this.onDelete,
  });

  final KinescopeDownloadInfo item;
  final VoidCallback onTap;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: DemoTheme.demoWhite,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  item.title ?? item.contentId,
                  style: const TextStyle(
                    color: DemoTheme.playlistTextPrimary,
                    fontSize: 16,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
              IconButton(
                onPressed: onDelete,
                icon: const Icon(Icons.delete_outline),
                color: DemoTheme.demoBlack,
                tooltip: 'Delete',
              ),
            ],
          ),
        ),
      ),
    );
  }
}
