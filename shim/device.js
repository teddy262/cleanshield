/* Android TV (Nvidia Shield) adapter. Same FrameDevice API as the Tizen version. */
(function () {
  'use strict';
  var pending = {};
  var counter = 0;

  window.__fdCb = function (id, err, value) {
    var entry = pending[id];
    if (!entry) return;
    delete pending[id];
    clearTimeout(entry.timer);
    entry.fn(err || null, value === null ? undefined : value);
  };

  function call(method, arg, done) {
    var bridge = window.AndroidBridge;
    if (!bridge) {
      done({ message: 'Open CleanFrame on the Shield to use this control.', detail: 'The web preview cannot control the TV.' });
      return;
    }
    var id = ++counter;
    pending[id] = {
      fn: done,
      timer: setTimeout(function () {
        delete pending[id];
        done({ message: 'The Shield did not respond. Please try again.' });
      }, 15000)
    };
    try {
      bridge[method](id, arg || '');
    } catch (err) {
      clearTimeout(pending[id].timer);
      delete pending[id];
      done({ message: 'The Shield could not open this screen.', detail: err && err.message });
    }
  }

  window.FrameDevice = {
    launch: function (brand, done) { call('launch', brand, done); },
    launchApp: function (id, done) { call('launchApp', id, done); },
    art: function (done) { call('art', '', done); },
    home: function (done) { call('home', '', done); },
    settings: function (done) { call('settings', '', done); },
    apps: function (done) { call('apps', '', done); }
  };
}());
