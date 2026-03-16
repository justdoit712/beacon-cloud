var vm = new Vue({
    el: '#dtapp',
    data: {
        sites: [],
        loading: false,
        chart: null,
        stats: {
            waiting: 0,
            success: 0,
            failed: 0,
            total: 0
        },
        chartMessage: '设置筛选条件后，可在这里查看图表反馈。'
    },
    methods: {
        initChart: function () {
            if (!this.chart) {
                this.chart = echarts.init(document.getElementById('pie'));
            }
        },
        resetForm: function () {
            if (this.loading) {
                return;
            }
            $("#start").val('');
            $("#end").val('');
            $("#clientID").val('');
            this.reload();
        },
        reload: function () {
            var myEcharts = this.chart;
            this.loading = true;
            this.chartMessage = '正在加载统计结果，请稍候...';

            var temp = {
                startTime: $("#start").val() === '' ? null : Date.parse($("#start").val()),
                endTime: $("#end").val() === '' ? null : Date.parse($("#end").val()),
                clientID: $("#clientID").val()
            };
            $.ajax({
                url: '/sys/echarts/pie',
                data: temp,
                dataType: 'json',
                success: function (r) {
                    var legendData = Array.isArray(r && r.legendData) ? r.legendData : [];
                    var seriesData = Array.isArray(r && r.seriesData) ? r.seriesData : [];
                    var waiting = vm.findSeriesValue(seriesData, '等待');
                    var success = vm.findSeriesValue(seriesData, '成功');
                    var failed = vm.findSeriesValue(seriesData, '失败');
                    var total = waiting + success + failed;

                    vm.stats = {
                        waiting: waiting,
                        success: success,
                        failed: failed,
                        total: total
                    };
                    vm.chartMessage = total > 0 ? '图表已刷新，可结合上方汇总卡片查看当前状态分布。' : '当前筛选条件下暂无统计数据。';

                    myEcharts.setOption({
                        title: {
                            text: '成功率统计',
                            subtext: total > 0 ? '真实有效' : '暂无数据',
                            x: 'center'
                        },
                        tooltip: {
                            trigger: 'item',
                            formatter: "{a} <br/>{b} : {c} ({d}%)"
                        },
                        legend: {
                            orient: 'vertical',
                            left: 'left',
                            data: legendData
                        },
                        series: [
                            {
                                name: '成功率统计',
                                type: 'pie',
                                radius: '75%',
                                center: ['60%', '60%'],
                                data: seriesData,
                                itemStyle: {
                                    emphasis: {
                                        shadowBlur: 10,
                                        shadowOffsetX: 0,
                                        shadowColor: 'rgba(0, 0, 0, 0.5)'
                                    }
                                }
                            }
                        ]
                    });
                },
                error: function () {
                    vm.chartMessage = '统计请求失败，请稍后重试。';
                    vm.stats = {
                        waiting: 0,
                        success: 0,
                        failed: 0,
                        total: 0
                    };
                    layer.alert(vm.chartMessage);
                },
                complete: function () {
                    vm.loading = false;
                    myEcharts.resize();
                }
            });
        },
        findSeriesValue: function (seriesData, targetName) {
            for (var i = 0; i < seriesData.length; i++) {
                if (seriesData[i] && seriesData[i].name === targetName) {
                    return Number(seriesData[i].value || 0);
                }
            }
            return 0;
        },
        handleResize: function () {
            if (this.chart) {
                this.chart.resize();
            }
        }
    },
    created: function () {
        $.get("../sys/clientbusiness/all", function (r) {
            vm.sites = r && (r.sites || r.data) ? (r.sites || r.data) : [];
        });
    },
    mounted: function () {
        this.initChart();
        this.reload();
        window.addEventListener('resize', this.handleResize);
    },
    beforeDestroy: function () {
        window.removeEventListener('resize', this.handleResize);
    }
});
