Ext.define('SYNO.SDS.XLPan.Application', {
	extend: 'SYNO.SDS.AppInstance',
	appWindowName: 'SYNO.SDS.XLPan.Main',
	constructor: function() {
		this.callParent(arguments);
	}
});

Ext.define('SYNO.SDS.XLPan.Main', {
	extend: 'SYNO.SDS.AppWindow',
	appInstance: null,

	constructor: function(cfg) {
		this.appInstance = cfg.appInstance;

		var config = Ext.apply({
			resizable: false,
			maximizable: true,
			minimizable: true,
			width: 1060,
			height: 680,
			layout: 'fit',
			items: [
				new Ext.BoxComponent({
					height: "100%",
					html: '<iframe src="/webman/3rdparty/pan-xunlei-com/index.cgi/" frameborder="0" marginheight="0" marginwidth="0" width="100%" height="100%"></iframe>'
				})
			]
		}, cfg);

		this.callParent([config]);
	}
});
