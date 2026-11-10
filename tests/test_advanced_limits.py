"""Regression: editing MIN/MAX must not fail due to stale DEFAULT/STEP."""
from pathlib import Path
import subprocess
import json
root = Path(__file__).resolve().parents[1]
js = (root / 'app/src/main/assets/ui/app.js').read_text(encoding='utf-8')
start = js.index('  function adjustAdvancedLimit(')
end = js.index('  function renderAdvanced()', start)
function = js[start:end]
script = r'''
'use strict';
const assert = require('node:assert/strict');
const cap = (v, low, high) => Math.max(low, Math.min(high, v));
''' + function + r'''
const before = {min:0,max:100,step:5,default:80};
const narrower = adjustAdvancedLimit(before,'max',50,0,100);
assert.deepEqual(narrower.bounds,{min:0,max:50,step:5,default:50});
assert.deepEqual(before,{min:0,max:100,step:5,default:80});
const raised = adjustAdvancedLimit(before,'min',95,0,100);
assert.deepEqual(raised.bounds,{min:95,max:100,step:5,default:95});
const crossed = adjustAdvancedLimit(before,'min',100,0,100);
assert.deepEqual(crossed.bounds,{min:100,max:100,step:1,default:100});
const lowerCross = adjustAdvancedLimit({min:10,max:30,step:10,default:25},'max',5,0,100);
assert.deepEqual(lowerCross.bounds,{min:5,max:5,step:1,default:5});
const broadened = adjustAdvancedLimit({min:-18,max:-1,step:1,default:-6},'min',-28,-30,-1);
assert.equal(broadened.bounds.min,-28);
assert.ok(adjustAdvancedLimit(before,'max',101,0,100).error);
assert.ok(adjustAdvancedLimit(before,'min',-1,0,100).error);
assert.ok(adjustAdvancedLimit(before,'default',101,0,100).error);
assert.ok(adjustAdvancedLimit(before,'step',102,0,100).error);
assert.ok(adjustAdvancedLimit(before,'min',NaN,0,100).error);
assert.ok(adjustAdvancedLimit(before,'min',2.3,0,100).error);
console.log('PASS: independent MIN/MAX changes, adjusted DEFAULT/STEP, strict safe bounds');
'''
subprocess.run(['node', '-e', script], check=True)
assert 'entered===\'\'?NaN:Number(entered)' in js
assert 'save({numericLimits:limits,[spec.key]:data[spec.key]})' in js
print('PASS: advanced limits UI wiring')
