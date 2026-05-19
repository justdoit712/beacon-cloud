$(function () {
    var option = {
        url: '../sys/api-gateway-filter/list',
        pagination: true,	//显示分页条
        sidePagination: 'server',//服务器端分页
        showRefresh: true,  //显示刷新按钮
        search: true,
        striped: true,     //设置为true会有隔行变色效果
        //idField: 'menuId',
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
            {title: 'id', field: 'id', sortable: true},
            {title: '服务', field: 'serviceName'},
            {title: 'Data ID', field: 'dataId'},
            {title: 'Group', field: 'group'},
            {title: '过滤器列表', field: 'filters'},
            {title: '读取状态', field: 'readStateText'},
            {title: '说明', field: 'message'}
        ]
    };
    $('#table').bootstrapTable(option);
});

var vm = new Vue({
    el: '#dtapp',
    data: {
        showList: true,
        title: null,
        filter: {}
    },
    methods: {
        reload: function (event) {
            vm.showList = true;
            $("#table").bootstrapTable('refresh');
        }
    }
});
