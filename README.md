# What is a Cartridge?

A Cartridge is a set of resources that are loaded into ADOP for a particular project. They may contain anything from a simple reference implementation for a technology to a set of best practice examples for building, deploying, and managing a technology stack that can be used by a project.

Please be informed that this cartridge was tailored a bit for DevOps Academy  [adop-doa-materials](https://github.com/Accenture/adop-doa-materials.git)

## Source code repositories

Cartridge loads the source code repositories

* [adop-cartridge-ansible-reference-role](https://github.com/Accenture/adop-cartridge-ansible-reference-role.git)
* [adop-cartridge-ansible-demo-scenario-playbook](https://github.com/Accenture/adop-cartridge-ansible-demo-scenario-playbook.git)

## Jenkins Jobs

This cartridge generates the Jenkins jobs and pipeline views to -

* Download the source code from Ansible Reference (SSL) role repository.
* Running sanity tests (Ansible Lint & Ansible Review) on the YAML.
* Running integration test on the YAML using Test Kitchen with Docker driver.
* Running the playbook.

# License
Please view [license information](LICENSE.md) for the software contained on this image.

## Documentation
Documentation will be captured within this README.md and this repository's Wiki.

## Issues
If you have any problems with or questions about this repository, please contact us through a GitHub issue.

## Contribute
You are invited to contribute new features, fixes, or updates, large or small; we are always thrilled to receive pull requests, and do our best to process them as fast as we can.

Before you start to code, we recommend discussing your plans through a GitHub issue, especially for more ambitious contributions. This gives other contributors a chance to point you in the right direction, give you feedback on your design, and help you find out if someone else is working on the same thing.
