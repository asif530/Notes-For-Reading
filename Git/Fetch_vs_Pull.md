Git Fetch vs Pull
The key difference between git fetch and git pull lies in how they interact with your local working directory and local branches after retrieving updates from a remote repository.

git fetch: 
Downloads changes from the remote repository to your local Git repository.
Updates the remote-tracking branches (e.g., origin/main, origin/feature-branch) in your local repository, reflecting the state of the remote.
Does not merge these changes into your current local branch or modify your working directory.
Allows you to inspect the changes from the remote before deciding whether and how to integrate them into your local work.

git pull:
Is a convenience command that essentially performs a git fetch followed by a git merge (or git rebase, depending on configuration).
Downloads changes from the remote repository and automatically merges them into your current local branch.
Modifies your local working directory to reflect the merged changes.
Can lead to merge conflicts if there are conflicting changes between your local branch and the remote changes.

====================================================================================================================================================================================

What is the difference between 'git pull' and 'git fetch'?
In the simplest terms, git pull does a git fetch followed by a git merge.

git fetch updates your remote-tracking branches under refs/remotes/<remote>/. 
This operation is safe to run at any time since it never changes any of your local branches under refs/heads.

git pull brings a local branch up-to-date with its remote version, while also updating your other remote-tracking branches.

From the Git documentation for git pull:
git pull runs git fetch with the given parameters and then depending on configuration options or command line flags, will call either git rebase or git merge to reconcile diverging branches.

====================================================================================================================================================================================
git pull tries to automatically merge after fetching commits. It is context sensitive, so all pulled commits will be merged into your currently active branch. git pull automatically merges the commits without letting you review them first. If you don’t carefully manage your branches, you may run into frequent conflicts.

git fetch gathers any commits from the target branch that do not exist in the current branch and stores them in your local repository. However, it does not merge them with your current branch. This is particularly useful if you need to keep your repository up to date, but are working on something that might break if you update your files. To integrate the commits into your current branch, you must use git merge afterwards.
====================================================================================================================================================================================

https://longair.net/blog/2012/05/07/the-most-confusing-git-terminology/ => Git Confusing terminology
https://longair.net/blog/2009/04/16/git-fetch-and-merge/








