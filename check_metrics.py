import json, subprocess

def api(path):
    result = subprocess.run(
        ['docker', 'exec', 'prometheus', 'wget', '-qO-',
         f'http://localhost:9090/api/v1{path}'],
        capture_output=True, text=True, timeout=10
    )
    return json.loads(result.stdout)

# Check scrape targets
print('=== Prometheus Scrape Targets ===')
d = api('/targets?state=active')
for t in d['data']['activeTargets']:
    job = t['labels']['job']
    health = t['health']
    scrape_url = t['scrapeUrl']
    last_err = t.get('lastError', '')
    print(f'  Job: {job:<20} Health: {health:<10} URL: {scrape_url}')
    if last_err:
        print(f'    Error: {last_err[:100]}')

print()
print('=== Prometheus WAL data check ===')
# Check if there is historical data at all for ai metrics
d = api('/query?query=ai_tokens_total{token_type="total"}')
results = d['data']['result']
if results:
    for r in results[:2]:
        print(f'  ai_tokens_total: {r["metric"]} = {r["value"]}')
else:
    print('  ai_tokens_total: no data (may need AI calls to be made)')

d = api('/query?query=ai_calls_total{type="all"}')
results = d['data']['result']
if results:
    for r in results[:2]:
        print(f'  ai_calls_total: {r["metric"]} = {r["value"]}')
else:
    print('  ai_calls_total: no data (may need AI calls to be made)')

d = api('/query?query=ai_calls_duration_seconds_count')
results = d['data']['result']
if results:
    for r in results[:2]:
        print(f'  ai_calls_duration_seconds_count: {r["metric"]} = {r["value"]}')
else:
    print('  ai_calls_duration_seconds_count: no data (may need AI calls to be made)')

# Check judge
d = api('/query?query=judge_container_pool_size')
results = d['data']['result']
if results:
    for r in results[:2]:
        print(f'  judge_container_pool_size: {r["metric"]} = {r["value"]}')
else:
    print('  judge_container_pool_size: no data')

d = api('/query?query=judge_container_metrics_created')
results = d['data']['result']
if results:
    for r in results[:2]:
        print(f'  judge_container_metrics_created: {r["metric"]} = {r["value"]}')
else:
    print('  judge_container_metrics_created: no data')
