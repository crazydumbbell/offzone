#!/usr/bin/env python3
"""Check report/metadata/graph agreement; does not verify UX or device behavior."""
import copy
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def check(metadata, graph):
    errors = []

    def require(condition, message):
        if not condition:
            errors.append(message)

    def index(items, label):
        result = {item['id']: item for item in items}
        require(len(result) == len(items), f'{label}: duplicate IDs')
        return result

    revision = metadata['strategy_revision']
    require(graph['strategy_revision'] == revision, 'strategy revision mismatch')
    contract = metadata['strategy_contract']
    require(metadata['primary_language'] == contract['primary_customer_language'],
            'primary language mismatch')
    require(graph['scope']['customer_language_primary'] == metadata['primary_language'].split('-')[0],
            'graph primary language mismatch')
    require(graph['scope']['contexts'] == [c['label_en'] for c in contract['contexts']],
            'customer contexts mismatch')
    require(metadata['store_listing_draft']['primary_locale'] == metadata['primary_language'],
            'store primary language mismatch')
    documents = ['ORCHESTRATOR.md', 'SALES_STRATEGY_REPORT.ko.md',
                 'UX_STRATEGY_REPORT.ko.md', 'APP_GRAPH.md']
    work = index(metadata['priorities_proposed'], 'metadata work')
    for document in documents:
        path = ROOT / document
        require(path.is_file(), f'missing document: {document}')
        if path.is_file():
            content = path.read_text().replace('**', '')
            require(f'Strategy revision: {revision}' in content, f'{document}: stale revision')
            for work_id in work:
                require(work_id in content, f'{document}: missing {work_id}')
    for path in metadata['artifacts'].values():
        require((ROOT / path).is_file(), f'missing artifact: {path}')

    features = index(metadata['features'], 'metadata features')
    graph_features = index(graph['features'], 'graph features')
    nodes = index(graph['nodes'], 'graph nodes')
    graph_work = index(graph['work_items'], 'graph work')
    require(features.keys() == graph_features.keys(), 'feature IDs mismatch')
    require(work.keys() == graph_work.keys(), 'work IDs mismatch')
    for feature_id, feature in graph_features.items():
        require(feature['status'] == features.get(feature_id, {}).get('status'),
                f'{feature_id}: implementation status mismatch')
        require(feature['node_id'] in nodes, f'{feature_id}: missing feature node')
        require(set(feature['work_ids']) <= work.keys(), f'{feature_id}: unknown work ID')
    for item in graph['work_items']:
        require(set(item['feature_ids']) <= features.keys(), f"{item['id']}: unknown feature")
        require(set(item['dependency_nodes']) <= nodes.keys(), f"{item['id']}: missing dependency")
    for edge in graph['edges']:
        require(edge['source'] in nodes and edge['target'] in nodes, f'dangling edge: {edge}')
    for item in graph['nodes'] + graph['edges']:
        require(item['confidence'] in ('EXTRACTED', 'INFERRED', 'AMBIGUOUS'),
                f'invalid confidence: {item}')
    refs = [ref for item in graph['nodes'] + graph['edges'] + graph['features']
            for ref in item['source_refs']]
    refs += [ref for feature in metadata['features'] for ref in feature['evidence']]
    for ref in refs:
        path = ROOT / ref['path']
        require(path.is_file(), f"missing source: {ref['path']}")
        if path.is_file() and 'line' in ref:
            require(1 <= ref['line'] <= len(path.read_text().splitlines()),
                    f'source line out of range: {ref}')
    for path, expected in graph['source_fingerprints'].items():
        source = ROOT / path
        require(source.is_file() and hashlib.sha256(source.read_bytes()).hexdigest() == expected,
                f'stale source fingerprint: {path}')
    for locale in metadata['languages']:
        listing = metadata['store_listing_draft'][locale]
        for key, limit in [('name', 30), ('subtitle', 30), ('promotional_text', 170),
                           ('keywords', 100), ('description', 4000)]:
            require(len(listing[key]) <= limit, f'{locale}/{key}: exceeds {limit}')
    return list(dict.fromkeys(errors))


def main():
    metadata = json.loads((ROOT / 'APP_METADATA.json').read_text())
    graph = json.loads((ROOT / 'APP_GRAPH.json').read_text())
    errors = check(metadata, graph)
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    if '--self-test' in sys.argv:
        mutations = [
            lambda g: g.update(strategy_revision='stale'),
            lambda g: g['features'][0].update(status='verified_without_evidence'),
            lambda g: g['features'].pop(),
            lambda g: g['edges'][0].update(target='missing-node'),
            lambda g: g['source_fingerprints'].update({next(iter(g['source_fingerprints'])): 'stale'}),
        ]
        for mutate in mutations:
            broken = copy.deepcopy(graph)
            mutate(broken)
            if not check(metadata, broken):
                raise AssertionError('An inconsistent graph was accepted')
        print('Self-check: five deliberate inconsistencies rejected; files unchanged.')
    print(f"Alignment {metadata['strategy_revision']}: {len(metadata['features'])} features, "
          f"{len(metadata['priorities_proposed'])} work IDs, sources and copy limits OK.")
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f'Invalid alignment input: {error}', file=sys.stderr)
        sys.exit(1)
