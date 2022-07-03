## 基于深度学习的医学图像分割框架：CAN

此代码是本人于2022年2月16日提出的创新点，并投稿SCI一区MAI期刊，现投稿状态为一修，并预计2022年8月20日再次提交论文。

论文框架如下:主要适用于医学大脑组织图像分割。

![image-20220702213225828](C:\Users\28635\AppData\Roaming\Typora\typora-user-images\image-20220702213225828.png)

网络框架由DenseUnet与各种注意力机制组合，性能较目前最好的方法高1%。

#### 一、认识医学图像数据集

**我们主要关注大脑**

医学图像数据集根据成像方式分为：**MRI(核磁共振成像)**、**CT**等，主要关注这两类，我们论文用到的数据集都是磁共振成像的-MRI

CAN使用到的数据集：

==**MACCAI2012**:==

**粗粒度类别28，细粒度类别139**

- 成人大脑数据集，共30个人，每个人的大脑都有256张切片，编号为1000_3等，训练集编号分别为：

  ```
  1000_3
  1001_3
  1002_3
  1006_3
  1007_3
  1008_3
  1009_3
  1010_3
  1011_3
  1012_3
  1013_3
  1014_3
  1015_3
  1017_3
  1036_3
  ```

- 测试集编号为：

  ```
  1003_3
  1004_3
  1018_3
  1019_3
  1101_3
  1104_3
  1107_3
  1110_3
  1113_3
  1122_3
  1005_3
  1116_3
  1119_3
  1125_3
  1128_3
  ```

- 三维图像后缀为.nii.gz或者.nii，查看三维图像使用以下工具:![image-20220612184039938](C:\Users\28635\AppData\Roaming\Typora\typora-user-images\image-20220612184039938.png)

- 双击三维.nii或者.nii.gz图像，默认使用ITK-SNAP软件打开，界面如下：

- 下图为MALC2012中1000_3.nii文件

  ![image-20220612184214038](C:\Users\28635\AppData\Roaming\Typora\typora-user-images\image-20220612184214038.png)

- 打开图像之后，在上述页面中拖入1000_3对应的标签文件：1003_glm.nii，可以直观看到不同标签不同区域，拖入时选择：

- ![image-20220612184900306](C:\Users\28635\AppData\Roaming\Typora\typora-user-images\image-20220612184900306.png)

  可得下面结果：

  ![image-20220612184455646](C:\Users\28635\AppData\Roaming\Typora\typora-user-images\image-20220612184455646.png)

==**dhcp**==：**标签类别：10**

- 婴儿数据集：共40个人，每个大脑的切片共256张。

- 可视化结果如图：

- ![image-20220612184630313](C:\Users\28635\AppData\Roaming\Typora\typora-user-images\image-20220612184630313.png)

- 训练集：

  ```
  01
  02
  03
  04
  05
  06
  07
  08
  09
  10
  11
  12
  13
  14
  15
  16
  17
  18
  19
  20
  21
  22
  23
  24
  25
  ```

- 测试集：

  ```
  26
  27
  28
  29
  30
  31
  32
  33
  34
  35
  36
  37
  38
  39
  40
  ```

==**pwml**==：**标签类别：6**

- 新生儿脑白质病变数据集，共54个，每个大脑切片均为256张

- 可视化界面：

  ![image-20220612184947693](C:\Users\28635\AppData\Roaming\Typora\typora-user-images\image-20220612184947693.png)

- 训练集：

  ```
  01
  02
  03
  04
  05
  06
  07
  08
  09
  10
  11
  12
  13
  14
  15
  16
  17
  18
  19
  20
  21
  22
  23
  24
  25
  26
  27
  28
  29
  30
  31
  32
  33
  34
  35
  36
  37
  38
  39
  ```

- 测试集：

  ```
  40
  41
  42
  43
  44
  45
  46
  47
  48
  49
  50
  51
  52
  53
  54
  ```

#### 二、CAN代码布局

```java
|-->code
    |-->Datasets
       |-->MALC2012(数据集1)
          |-->training-imagesnpy (1000_3.npy and 1000_3_brainmask.npy)
          |-->training-labels-remapnpy (1000_3_glm.npy)
              (labels for MALC-coarse segmentation)
          |-->training-labels139 (1000_3_glm.npy)
              (labels for MALC-fine-grained segmentation)
          |-->testing-imagesnpy (1003_3.npy and 1000_3_brainmask.npy)
          |-->testing-labels-remapnpy (1003_3_glm.npy）
              labels for MALC-coarse segmentation)
          |-->testing-labels139 (1003_3_glm.npy）
              (labels for MALC-fine-grained segmentation)
       |-->dhcp(数据集2)
         |-->training-imagesnpy (01.npy and 01_brainmask.npy)
         |-->training-labels-remapnpy (26_glm.npy)
             (labels for dhcp segmentation)
         |-->testing-imagesnpy (26.npy and 26_brainmask.npy)
         |-->testing-labels-remapnpy (26_glm.npy)
             (labels for dhcp segmentation)
       |-->pwml(数据集3)
         |-->training-imagesnpy (01.npy and 01_brainmask.npy)
         |-->training-labels-remapnpy (01_glm.npy)
             (labels for pwml segmentation)
         |-->testing-imagesnpy (40.npy and 40_brainmask.npy)
         |-->testing-labels-remapnpy (40_glm.npy)
             (labels for pwml segmentation)
    |-->segmentation
       |-->data_list(训练集，测试集列表)
           |-->train_malc.txt (malc训练集的列表)
           |-->text_malc.txt (malc测试集的列表)
           |-->train_pwml.txt (pwml训练集的列表)
           |-->text_pwml.txt (pwml测试集的列表)
           |-->train_dhcp.txt (dhcp训练集的列表)
           |-->text_dhcp.txt (dhcp测试集的列表)
       |-->data_utils(不用过多关注)
           |-->lookup_tables.py (学习率)
           |-->lr_scheduler.py (学习率)
           |-->surface_distance.py (学习率)
           |-->utils.py (将MALC2012分为粗粒度和细粒度的代码)
       |-->Modules (模型架构：重点理解，后续消融实验改动地方)
           |-->backbone.py 模型架构)
           |-->Blockmodule.py (模型架构的上采样等卷积功能)
           |-->channel_attention.py (通道注意力，对应CA模块)
           |-->grid_attention (双门控空间注意力，对应DSA模块)
           |-->lossmodule (损失函数)
           |-->mri.py (切片s的选取)
           |-->networkother.py (不用关注)
           |-->non_local.py (non_local注意力，对应NSA模块)
           |-->scale1.py (尺度注意力，对应SA模块)
           |-->SEmudule.py
               (存放CSE,SSE,CSSE等注意力，我们论文没有使用这些，后续可以作为对比实验)
       |-->save_model
           |-->MALC_coarse（存放MALC2012数据集中粗粒度的模型）
               |-->checkpoint_pretrain.pth（预训练模型）
               |-->checkpoint_**.pth
                   (自己跑的模型,根据最终跑的最好模型来选择是哪个)
           |-->MALC_fine (存放MALC2012数据集中细粒度的模型）
           |-->dhcp (存放dhcp数据集中细粒度的模型)
           |-->pwml (存放pwml数据集中细粒度的模型)
       |-->seed.py
           (生成每个数据集对应类别的预训练模型，改变类别和切片数量，还有存储路径即可)
       |-->train.py（训练代码)
       |-->test_fine.py (测试MALC2012数据集的细粒度代码）)
       |-->test_coarse.py (测试MALC2012数据集的粗粒度代码)
```

#### 三、跑代码相关命令

- 1.git clone 命令下载代码
- 2.输入python train.py运行代码，期间提示没有的包，输入pip install **安装依赖包
- 3.关闭服务器也能后台继续跑的nohup python train.py >file.log 2>&1 &
- 4.跑三个不同数据集需要修改的地方：
  - （1）train.py中的RESUME_PATH = '/opt/data/private/qianmi/CAN/save_model/pwml/checkpoint_pretrain.pth.tar'改为对应数据集的预训练模型；
  - （2）train.py中SAVE_DIR = '/opt/data/private/qianmi/CAN/save_model/pwml/'改为对应数据集的存储路径，后续训练出来的模型会放在此路径下；
  - （3）train.py中NUM_CLASS = 6改为对应数据集需要分割的类别，MALC2012=28/139，dhcp=10，pwml=6
  - （4）train.py中DATA_DIR = '/opt/data/private/CAN/Datasets/pwml/'需要改为对应数据集的存放路径
  - （5）Modules.mri.py中if num_class is 6:需要改为对应数据集的类别；image_list = os.path.join(list_dir, 'train_pwml.txt')改为对应数据集遍历txt文件。对应的image_list = os.path.join(list_dir, 'text_pwml.txt')也要改。

- 注意在服务器上面运行train.py时，train.py不能出现中文。

```java
RESUME_PATH: directory to resume the model
SAVE_DIR: directory to save the model
NUM_CLASS: label classes +1 (background)
TWO_STAGES: use two stage training
RESUME_PRETRAIN: set False if want to train from epoch 0, True to resume the pretrained epoch

-b-train: For NVIDIA TITAN XP GPU with 12 GB memory, use batch size of 4. 
-b-test: use 2, must be bigger than 1.
-num-slices: slice thickness used for Spatial Encoding Module, use 3 for coase-grained segmentation and 7 for for-grained segmentation.
--lr-scheduler: used poly
--lr: for train from scratch, use 0.01 and 0.02 for coarse and fine-grained respecitvely, for pretrain, use 0.001 and 0.005 for coarse and fine-grained respecitvely.
```

#### 四、推荐论文撰写相关工具

图片转公式的工具：     ![image-20220611203912840](C:\Users\28635\AppData\Roaming\Typora\typora-user-images\image-20220611203912840.png)

裁剪pdf的工具：            ![image-20220611203944291](C:\Users\28635\AppData\Roaming\Typora\typora-user-images\image-20220611203944291.png)

生成表格的工具 ：         https://www.tablesgenerator.com/latex_tables#

latex编辑在线工具：     https://cn.overleaf.com/project

绘图工具：                     ![image-20220611220109910](C:\Users\28635\AppData\Roaming\Typora\typora-user-images\image-20220611220109910.png)

注意总框图最好生成.eps文件，不会压缩太大分辨率，其他框图可以转换为.pdf文件。

**visio转换为eps的方法：将visio另存为.pdf格式，再利用Acrobat软件打开，将其另存为.eps格式**