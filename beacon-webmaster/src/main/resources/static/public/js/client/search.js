$(function () {
    var option = {
        url: '../sys/search/list',
        pagination: true,
        sidePagination: 'server',
        striped: true,
        responseHandler: function (res) {
            if (res && res.code === 0) {
                res.total = (typeof res.total === 'number') ? res.total : 0;
                res.rows = Array.isArray(res.rows) ? res.rows : [];
                return res;
            }
            return { total: 0, rows: [] };
        },
        queryParams: function (params) {
            return {
                size: params.limit,
                from: params.offset,
                content: $("#content").val(),
                starttime: $("#starttime").val() === '' ? null : Date.parse($("#starttime").val()),
                stoptime: $("#stoptime").val() === '' ? null : Date.parse($("#stoptime").val()),
                mobile: $("#mobile").val(),
                clientID: $("#clientID").val()
            };
        },
        columns: [
            {
                field: 'id',
                title: '序号',
                width: 40,
                formatter: function (value, row, index) {
                    var pageSize = $('#table').bootstrapTable('getOptions').pageSize;
                    var pageNumber = $('#table').bootstrapTable('getOptions').pageNumber;
                    return pageSize * (pageNumber - 1) + index + 1;
                }
            },
            { field: 'corpname', title: '客户名称' },
            { field: 'sendTimeStr', title: '发送时间' },
            {
                field: 'reportState',
                title: '状态',
                formatter: function (value) {
                    if (value === 0) {
                        return '等待';
                    }
                    if (value === 1) {
                        return '成功';
                    }
                    return '失败';
                }
            },
            {
                field: 'operatorId',
                title: '运营商',
                formatter: function (value) {
                    if (value === 1) {
                        return '移动';
                    }
                    if (value === 2) {
                        return '联通';
                    }
                    if (value === 3) {
                        return '电信';
                    }
                    return '未知';
                }
            },
            { field: 'errorMsg', title: '错误原因' },
            { field: 'srcNumber', title: '发送号' },
            { field: 'mobile', title: '手机号' },
            { field: 'text', title: '短信内容' }
        ]
    };
    $('#table').bootstrapTable(option);
});

var vm = new Vue({
    el: '#dtapp',
    data: {
        sites: []
    },
    methods: {
        reload: function () {
            $("#table").bootstrapTable('refresh', { pageNumber: 1 });
        },
        resetForm: function () {
            $("#content").val('');
            $("#mobile").val('');
            $("#starttime").val('');
            $("#stoptime").val('');
            $("#clientID").val('');
            this.reload();
        }
    },
    created: function () {
        $.get("../sys/clientbusiness/all", function (r) {
            vm.sites = (r && r.data) ? r.data : [];
        });
    }
});

